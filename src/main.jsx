import React, { useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Aperture,
  Archive,
  ArrowDownToLine,
  ArrowUpRight,
  ChevronDown,
  CircleHelp,
  Clock3,
  Command,
  Download,
  Ellipsis,
  FileImage,
  FolderKanban,
  GalleryHorizontalEnd,
  Grid2X2,
  ImagePlus,
  Layers3,
  LayoutDashboard,
  Menu,
  MessageSquareText,
  MoreHorizontal,
  PanelRight,
  Play,
  Plus,
  Search,
  Settings2,
  Sparkles,
  UsersRound,
  WandSparkles,
  X,
} from 'lucide-react'
import './styles.css'

const assets = [
  { id: 1, title: '午夜便利店', type: '视频', date: '刚刚', status: '生成中', image: 'https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=900&q=80' },
  { id: 2, title: '雨中霓虹街', type: '图片', date: '今天 14:32', status: '已完成', image: 'https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=900&q=80' },
  { id: 3, title: '山谷列车镜头', type: '视频', date: '今天 11:08', status: '已完成', image: 'https://images.unsplash.com/photo-1473445361085-b9a07f55608b?auto=format&fit=crop&w=900&q=80' },
  { id: 4, title: '未来城市概念', type: '图片', date: '昨天 22:16', status: '已完成', image: 'https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=900&q=80' },
]

const navItems = [
  { label: '总览', icon: LayoutDashboard },
  { label: '画布创作', icon: WandSparkles, active: true },
  { label: '项目', icon: FolderKanban },
  { label: '资产', icon: Archive },
]

function App() {
  const [prompt, setPrompt] = useState('一个穿红色风衣的人走进午夜便利店，镜头缓慢推进，霓虹灯倒映在湿漉漉的街道上，电影感')
  const [mode, setMode] = useState('视频')
  const [isGenerating, setIsGenerating] = useState(false)
  const [activeHistory, setActiveHistory] = useState('个人历史')
  const [showAssets, setShowAssets] = useState(true)

  const selectedAsset = useMemo(() => assets.find((asset) => asset.id === 1), [])

  const handleGenerate = () => {
    setIsGenerating(true)
    window.setTimeout(() => setIsGenerating(false), 1800)
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row">
          <div className="brand-mark"><Aperture size={18} strokeWidth={2.4} /></div>
          <div>
            <div className="brand-name">Semple</div>
            <div className="brand-caption">AIGC Canvas</div>
          </div>
          <button className="icon-button subtle" aria-label="收起侧栏"><Menu size={17} /></button>
        </div>

        <button className="new-project"><Plus size={17} /> 新建项目 <span>⌘ N</span></button>

        <div className="nav-section">
          <div className="section-label">工作台</div>
          {navItems.map(({ label, icon: Icon, active }) => (
            <button className={`nav-item ${active ? 'active' : ''}`} key={label}>
              <Icon size={17} /> <span>{label}</span>{active && <span className="nav-dot" />}
            </button>
          ))}
        </div>

        <div className="nav-section asset-history-section">
          <button className="section-label section-button" onClick={() => setShowAssets((value) => !value)}>
            <span>资产历史</span><ChevronDown size={14} className={showAssets ? '' : 'rotated'} />
          </button>
          {showAssets && (
            <div className="history-tree">
              <button className={`history-item ${activeHistory === '个人历史' ? 'selected' : ''}`} onClick={() => setActiveHistory('个人历史')}>
                <Clock3 size={15} /><span>个人历史</span><span className="history-count">24</span>
              </button>
              <button className={`history-item ${activeHistory === '团队历史' ? 'selected' : ''}`} onClick={() => setActiveHistory('团队历史')}>
                <UsersRound size={15} /><span>团队历史</span><span className="history-count">08</span>
              </button>
            </div>
          )}
        </div>

        <div className="sidebar-bottom">
          <div className="usage-card">
            <div className="usage-top"><span>本月算力</span><span>68%</span></div>
            <div className="progress"><span style={{ width: '68%' }} /></div>
            <div className="usage-bottom"><span>6,820 / 10,000 积分</span><button>升级</button></div>
          </div>
          <button className="nav-item"><Settings2 size={17} /><span>设置</span></button>
          <div className="profile-row"><div className="avatar">林</div><div className="profile-copy"><strong>林默</strong><span>创作团队</span></div><MoreHorizontal size={17} className="muted-icon" /></div>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <div className="breadcrumbs"><span>项目</span><span className="slash">/</span><strong>城市夜行 · 01</strong><span className="saved"><span className="saved-dot" /> 已保存</span></div>
          <div className="top-actions"><button className="ghost-button"><CircleHelp size={16} /> 帮助</button><button className="ghost-button"><Command size={15} /> 快捷键</button><button className="avatar small">林</button></div>
        </header>

        <div className="workspace">
          <section className="canvas-column">
            <div className="canvas-toolbar">
              <div><h1>画布创作</h1><p>把灵感变成可继续编辑的镜头</p></div>
              <div className="toolbar-actions"><button className="square-button"><Grid2X2 size={17} /></button><button className="square-button"><PanelRight size={17} /></button><button className="square-button"><Ellipsis size={17} /></button></div>
            </div>

            <div className="canvas-stage">
              <div className="stage-grid" />
              <div className="canvas-node prompt-node">
                <div className="node-header"><span className="node-kind"><Sparkles size={14} /> Prompt</span><MoreHorizontal size={15} /></div>
                <div className="prompt-copy">{prompt}</div>
                <div className="node-footer"><span className="tag">中文</span><span className="tag">电影感</span><span className="node-status"><span className="live-dot" /> 已连接</span></div>
              </div>
              <div className="connector connector-a" />
              <div className="canvas-node output-node">
                <div className="node-header"><span className="node-kind"><Play size={14} fill="currentColor" /> 生成预览</span><span className="node-number">01</span></div>
                <div className="preview-frame"><img src={selectedAsset.image} alt="生成预览" /><div className="preview-overlay"><button className="play-button"><Play size={18} fill="currentColor" /></button></div><span className="duration">00:05</span></div>
                <div className="node-footer"><span className="tag">16:9</span><span className="tag">720p</span><span className="node-status">{isGenerating ? '生成中' : '已完成'}</span></div>
              </div>
              <div className="connector connector-b" />
              <button className="add-node"><Plus size={17} /></button>
              <div className="canvas-hint"><span>拖拽节点连接创作流程</span><span className="hint-divider" /><span>滚轮缩放</span></div>
            </div>

            <div className="composer">
              <div className="mode-tabs">{['视频', '图片', '剧本'].map((item) => <button key={item} className={mode === item ? 'active' : ''} onClick={() => setMode(item)}>{item}</button>)}</div>
              <textarea value={prompt} onChange={(event) => setPrompt(event.target.value)} aria-label="创作提示词" />
              <div className="composer-bottom"><div className="composer-tools"><button className="tool-button"><ImagePlus size={16} /> 参考图</button><button className="tool-button"><Layers3 size={16} /> 分镜</button><span className="char-count">{prompt.length}/2000</span></div><button className="generate-button" onClick={handleGenerate} disabled={isGenerating}><Sparkles size={17} /> {isGenerating ? '生成中…' : '开始生成'} <span>⌘ ↵</span></button></div>
            </div>
          </section>

          <aside className="right-panel">
            <div className="panel-header"><div><h2>资产</h2><p>{activeHistory} · 最近生成</p></div><button className="icon-button"><Search size={17} /></button></div>
            <div className="asset-filter"><button className="filter-active">全部 <span>12</span></button><button>图片 <span>08</span></button><button>视频 <span>04</span></button></div>
            <div className="asset-list">{assets.map((asset) => <article className="asset-card" key={asset.id}><div className="asset-thumb"><img src={asset.image} alt={asset.title} />{asset.status === '生成中' && <div className="progress-ring"><span>42%</span></div>}<span className={`asset-type ${asset.type === '视频' ? 'video' : ''}`}>{asset.type}</span></div><div className="asset-meta"><div className="asset-title-row"><strong>{asset.title}</strong><button className="icon-button tiny"><MoreHorizontal size={15} /></button></div><span>{asset.date}</span></div></article>)}</div>
            <button className="all-assets"><GalleryHorizontalEnd size={16} /> 查看全部资产 <ArrowUpRight size={15} /></button>
            <div className="panel-divider" />
            <div className="task-panel"><div className="task-heading"><span>生成队列</span><span className="queue-pill">1 个任务</span></div><div className="task-row"><div className="task-icon"><WandSparkles size={15} /></div><div className="task-copy"><strong>午夜便利店</strong><span>视频 · 720p</span></div><span className="task-progress">42%</span></div></div>
          </aside>
        </div>
      </main>
    </div>
  )
}

createRoot(document.getElementById('root')).render(<App />)
