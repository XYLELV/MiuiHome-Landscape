"""Main application window."""

from __future__ import annotations

import threading
import webbrowser
from pathlib import Path
from tkinter import filedialog, messagebox
from typing import Optional

import customtkinter as ctk

from .. import config, loader_detector, modrinth_api, updater
from ..loader_detector import ModLoader
from ..mod_scanner import ModInfo, scan_mods

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")

# Status badge colours
STATUS_COLORS = {
    "unknown":         ("#888888", "#555555"),
    "up_to_date":      ("#2ECC71", "#1A7A43"),
    "outdated":        ("#E74C3C", "#8B1A1A"),
    "not_on_modrinth": ("#95A5A6", "#4A5568"),
}
STATUS_LABELS = {
    "unknown":         "检查中…",
    "up_to_date":      "已是最新",
    "outdated":        "有更新",
    "not_on_modrinth": "未收录",
}


class ModRow(ctk.CTkFrame):
    def __init__(self, master, mod: ModInfo, on_update, **kwargs):
        super().__init__(master, corner_radius=8, **kwargs)
        self.mod = mod
        self.on_update = on_update
        self._build()

    def _build(self):
        self.grid_columnconfigure(1, weight=1)

        # Icon placeholder (loader colour strip)
        strip = ctk.CTkFrame(self, width=6, corner_radius=3,
                             fg_color="#4A90D9")
        strip.grid(row=0, column=0, rowspan=2, padx=(8, 6), pady=8, sticky="ns")

        # Name + version
        name_frame = ctk.CTkFrame(self, fg_color="transparent")
        name_frame.grid(row=0, column=1, sticky="w", padx=4, pady=(8, 0))

        self.name_lbl = ctk.CTkLabel(
            name_frame,
            text=self.mod.display_name,
            font=ctk.CTkFont(size=14, weight="bold"),
            anchor="w",
        )
        self.name_lbl.pack(side="left")

        self.ver_lbl = ctk.CTkLabel(
            name_frame,
            text=f"  v{self.mod.version}" if self.mod.version else "",
            font=ctk.CTkFont(size=12),
            text_color="#888888",
            anchor="w",
        )
        self.ver_lbl.pack(side="left")

        # Description
        desc = (self.mod.description or "")[:120]
        if desc:
            self.desc_lbl = ctk.CTkLabel(
                self, text=desc,
                font=ctk.CTkFont(size=11),
                text_color="#AAAAAA",
                anchor="w",
                wraplength=500,
            )
            self.desc_lbl.grid(row=1, column=1, sticky="w", padx=4, pady=(0, 8))

        # Right side buttons
        btn_frame = ctk.CTkFrame(self, fg_color="transparent")
        btn_frame.grid(row=0, column=2, rowspan=2, padx=8, pady=8, sticky="e")

        # Status badge
        fg, hover = STATUS_COLORS.get(self.mod.status, STATUS_COLORS["unknown"])
        self.status_badge = ctk.CTkButton(
            btn_frame,
            text=STATUS_LABELS.get(self.mod.status, "…"),
            width=90, height=28,
            corner_radius=14,
            fg_color=fg, hover_color=hover,
            font=ctk.CTkFont(size=11),
            state="disabled",
        )
        self.status_badge.pack(side="left", padx=4)

        # Modrinth link
        if self.mod.modrinth_project_url:
            self.link_btn = ctk.CTkButton(
                btn_frame, text="Modrinth",
                width=80, height=28,
                corner_radius=14,
                fg_color="#1BD96A", hover_color="#15A052",
                text_color="#000000",
                font=ctk.CTkFont(size=11),
                command=lambda: webbrowser.open(self.mod.modrinth_project_url),
            )
            self.link_btn.pack(side="left", padx=4)

        # Update button
        self.update_btn = ctk.CTkButton(
            btn_frame,
            text="更新",
            width=70, height=28,
            corner_radius=14,
            fg_color="#E74C3C", hover_color="#922B21",
            font=ctk.CTkFont(size=11, weight="bold"),
            command=self._do_update,
            state="normal" if self.mod.update_available else "disabled",
        )
        self.update_btn.pack(side="left", padx=4)

    def refresh_status(self):
        fg, hover = STATUS_COLORS.get(self.mod.status, STATUS_COLORS["unknown"])
        self.status_badge.configure(
            text=STATUS_LABELS.get(self.mod.status, "…"),
            fg_color=fg, hover_color=hover,
        )
        self.update_btn.configure(
            state="normal" if self.mod.update_available else "disabled"
        )
        ver_txt = f"  v{self.mod.version}" if self.mod.version else ""
        self.ver_lbl.configure(text=ver_txt)

        # Add Modrinth button if it appeared after check
        if self.mod.modrinth_project_url and not hasattr(self, "link_btn"):
            btn_frame = self.status_badge.master
            self.link_btn = ctk.CTkButton(
                btn_frame, text="Modrinth",
                width=80, height=28,
                corner_radius=14,
                fg_color="#1BD96A", hover_color="#15A052",
                text_color="#000000",
                font=ctk.CTkFont(size=11),
                command=lambda: webbrowser.open(self.mod.modrinth_project_url),
            )
            self.link_btn.pack(side="left", padx=4, before=self.update_btn)

    def _do_update(self):
        self.update_btn.configure(state="disabled", text="下载中…")
        self.on_update(self.mod, self)


class App(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("Minecraft Mod 管理器")
        self.geometry("900x650")
        self.minsize(700, 450)

        self._cfg = config.load()
        self._mods: list[ModInfo] = []
        self._rows: list[ModRow] = []
        self._loader = ModLoader.UNKNOWN
        self._game_version = ""

        self._build_ui()
        self._load_directory(self._cfg.get("minecraft_dir", ""), initial=True)

    # ── UI construction ──────────────────────────────────────────────────────

    def _build_ui(self):
        self.grid_rowconfigure(1, weight=1)
        self.grid_columnconfigure(0, weight=1)

        # Top bar
        topbar = ctk.CTkFrame(self, height=64, corner_radius=0)
        topbar.grid(row=0, column=0, sticky="ew")
        topbar.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(topbar, text="MC Mod 管理器",
                     font=ctk.CTkFont(size=18, weight="bold")).grid(
            row=0, column=0, padx=16, pady=8)

        # Directory path entry
        self._dir_var = ctk.StringVar(value=self._cfg.get("minecraft_dir", ""))
        dir_entry = ctk.CTkEntry(topbar, textvariable=self._dir_var,
                                 placeholder_text=".minecraft 目录…")
        dir_entry.grid(row=0, column=1, padx=8, pady=14, sticky="ew")
        dir_entry.bind("<Return>", lambda _: self._load_directory(self._dir_var.get()))

        ctk.CTkButton(topbar, text="浏览", width=60,
                      command=self._browse).grid(row=0, column=2, padx=4)

        ctk.CTkButton(topbar, text="扫描", width=60,
                      fg_color="#2980B9", hover_color="#1A5276",
                      command=self._scan).grid(row=0, column=3, padx=4)

        ctk.CTkButton(topbar, text="检查更新", width=90,
                      fg_color="#27AE60", hover_color="#1A6B3C",
                      command=self._check_updates).grid(row=0, column=4, padx=4)

        ctk.CTkButton(topbar, text="全部更新", width=90,
                      fg_color="#E74C3C", hover_color="#922B21",
                      command=self._update_all).grid(row=0, column=5, padx=(4, 16))

        # Status / loader bar
        statusbar = ctk.CTkFrame(self, height=32, corner_radius=0,
                                 fg_color=("#DDDDDD", "#2A2A2A"))
        statusbar.grid(row=1, column=0, sticky="ew")
        statusbar.grid_columnconfigure(1, weight=1)

        self._loader_badge = ctk.CTkLabel(
            statusbar, text="Loader: —",
            font=ctk.CTkFont(size=12, weight="bold"),
            text_color="#AAAAAA",
        )
        self._loader_badge.grid(row=0, column=0, padx=12, pady=4)

        self._gv_lbl = ctk.CTkLabel(
            statusbar, text="",
            font=ctk.CTkFont(size=12),
            text_color="#888888",
        )
        self._gv_lbl.grid(row=0, column=1, padx=4, pady=4, sticky="w")

        self._status_lbl = ctk.CTkLabel(
            statusbar, text="",
            font=ctk.CTkFont(size=12),
            text_color="#888888",
        )
        self._status_lbl.grid(row=0, column=2, padx=12, pady=4, sticky="e")

        # Scrollable mod list
        self._scroll = ctk.CTkScrollableFrame(self, corner_radius=0)
        self._scroll.grid(row=2, column=0, sticky="nsew", padx=0, pady=0)
        self._scroll.grid_columnconfigure(0, weight=1)
        self.grid_rowconfigure(2, weight=1)

        # Empty state label
        self._empty_lbl = ctk.CTkLabel(
            self._scroll,
            text="请选择 .minecraft 目录后点击「扫描」",
            font=ctk.CTkFont(size=14),
            text_color="#666666",
        )
        self._empty_lbl.grid(row=0, column=0, pady=60)

    # ── Directory handling ────────────────────────────────────────────────────

    def _browse(self):
        d = filedialog.askdirectory(title="选择 .minecraft 目录")
        if d:
            self._dir_var.set(d)
            self._load_directory(d)

    def _load_directory(self, path: str, initial: bool = False):
        mc_dir = Path(path)
        if not mc_dir.exists():
            if not initial:
                self._set_status("目录不存在")
            return
        self._cfg["minecraft_dir"] = str(mc_dir)
        config.save(self._cfg)
        self._loader, self._game_version = loader_detector.detect(mc_dir)
        self._loader_badge.configure(
            text=f"Loader: {self._loader.display()}",
            text_color=self._loader.color(),
        )
        self._gv_lbl.configure(
            text=f"MC {self._game_version}" if self._game_version else ""
        )
        self._scan()

    def _scan(self):
        mc_dir = Path(self._dir_var.get())
        mods_dir = mc_dir / "mods"
        if not mods_dir.exists():
            self._set_status("未找到 mods 目录")
            self._clear_list()
            return
        self._mods = scan_mods(mods_dir)
        self._render_list()
        self._set_status(f"已扫描到 {len(self._mods)} 个 Mod")

    # ── List rendering ────────────────────────────────────────────────────────

    def _clear_list(self):
        for row in self._rows:
            row.destroy()
        self._rows.clear()

    def _render_list(self):
        self._clear_list()
        if not self._mods:
            self._empty_lbl.configure(text="mods 目录为空")
            self._empty_lbl.grid()
            return
        self._empty_lbl.grid_remove()
        for i, mod in enumerate(self._mods):
            row = ModRow(
                self._scroll, mod,
                on_update=self._do_single_update,
                fg_color=("#F0F0F0", "#2D2D2D") if i % 2 == 0 else ("#E8E8E8", "#262626"),
            )
            row.grid(row=i, column=0, sticky="ew", padx=8, pady=3)
            self._rows.append(row)

    # ── Update checking ───────────────────────────────────────────────────────

    def _check_updates(self):
        if not self._mods:
            self._scan()
            if not self._mods:
                return
        self._set_status("正在检查更新…")

        def _worker():
            total = len(self._mods)
            for i, mod in enumerate(self._mods):
                self._set_status(f"检查中 {i+1}/{total}…")
                # Per-mod check using hash lookup + version compare
                lookup = modrinth_api.lookup_hashes([mod.sha512])
                ver_obj = lookup.get(mod.sha512)
                if ver_obj is None:
                    mod.status = "not_on_modrinth"
                else:
                    mod.modrinth_version_id = ver_obj.get("id", "")
                    mod.modrinth_project_id = ver_obj.get("project_id", "")
                    mod.modrinth_project_url = (
                        f"https://modrinth.com/mod/{mod.modrinth_project_id}"
                    )
                    loaders = (
                        [self._loader.value]
                        if self._loader != ModLoader.UNKNOWN
                        else []
                    )
                    gv = [self._game_version] if self._game_version else []
                    latest = modrinth_api.get_latest_version(
                        mod.modrinth_project_id, loaders, gv
                    )
                    if latest is None:
                        mod.status = "up_to_date"
                    else:
                        mod.latest_version_number = latest.get("version_number", "")
                        mod.latest_version_id = latest.get("id", "")
                        url, fname = modrinth_api.get_download_info(latest)
                        mod.download_url = url
                        mod.download_filename = fname
                        mod.status = (
                            "up_to_date"
                            if latest["id"] == mod.modrinth_version_id
                            else "outdated"
                        )
                self.after(0, lambda r=self._rows[i]: r.refresh_status())

            outdated = sum(1 for m in self._mods if m.update_available)
            self.after(
                0,
                lambda: self._set_status(
                    f"检查完毕 — {outdated} 个可更新"
                    if outdated
                    else "所有 Mod 均为最新"
                ),
            )

        threading.Thread(target=_worker, daemon=True).start()

    # ── Download / update ─────────────────────────────────────────────────────

    def _do_single_update(self, mod: ModInfo, row: ModRow):
        mods_dir = Path(self._dir_var.get()) / "mods"

        def _worker():
            try:
                updater.download_update(mod, mods_dir)
                self.after(0, lambda: (
                    row.refresh_status(),
                    self._set_status(f"✓ {mod.display_name} 更新完成"),
                ))
            except Exception as exc:
                self.after(0, lambda: (
                    messagebox.showerror("更新失败", str(exc)),
                    row.update_btn.configure(state="normal", text="更新"),
                ))

        threading.Thread(target=_worker, daemon=True).start()

    def _update_all(self):
        outdated = [m for m in self._mods if m.update_available]
        if not outdated:
            messagebox.showinfo("提示", "没有可更新的 Mod")
            return
        if not messagebox.askyesno("确认", f"将更新 {len(outdated)} 个 Mod，继续吗？"):
            return
        mods_dir = Path(self._dir_var.get()) / "mods"

        def _worker():
            for i, mod in enumerate(outdated):
                self.after(0, lambda m=mod: self._set_status(
                    f"更新中 {i+1}/{len(outdated)}: {m.display_name}…"
                ))
                try:
                    updater.download_update(mod, mods_dir)
                    # find and refresh its row
                    for row in self._rows:
                        if row.mod is mod:
                            self.after(0, row.refresh_status)
                            break
                except Exception:
                    pass
            self.after(0, lambda: self._set_status("全部更新完成"))

        threading.Thread(target=_worker, daemon=True).start()

    # ── Helper ────────────────────────────────────────────────────────────────

    def _set_status(self, text: str):
        self._status_lbl.configure(text=text)


def run():
    app = App()
    app.mainloop()
