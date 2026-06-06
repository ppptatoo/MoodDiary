# 截图文件夹

请将 App 界面截图放在此目录下，建议文件名：

| 文件名 | 对应页面 |
|--------|----------|
| `home.png` | 首页日历 |
| `record.png` | 记录心情 |
| `list.png` | 历史列表 |
| `detail.png` | 日记详情 |
| `stats.png` | 情绪统计 |
| `chat.png` | AI 聊天 |
| `profile.png` | 个人中心 |

放好截图后，打开 `docs/index.html`，将 gallery 区域的占位 div 替换为：

```html
<div class="gallery-item" style="background:none;border:none;">
    <img src="screenshots/home.png" alt="首页" style="width:100%;height:100%;object-fit:cover;border-radius:10px;">
</div>
```

> 💡 建议截图尺寸：宽 360px 以上，PNG 格式，保持 9:19.5 比例效果最佳。
