import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import {
  AlarmClock, ArrowDown, ArrowRight, Bell, Brush, Cherry, CircleCheck, Clock,
  Connection, Delete, Document, DocumentAdd, DocumentCopy, Download, Expand, Fold,
  Folder, Grid, InfoFilled, Link, Lock, MagicStick, Monitor, Moon, Notebook,
  OfficeBuilding, Plus, Promotion, QuestionFilled, Rank, Reading, Refresh, Setting, Star,
  SuccessFilled, Sunny, Timer, TopRight, Upload, User, VideoPlay, View, Warning,
  WarningFilled
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { permissionDirective } from './directives/permission'
import './styles/index.scss'

// Apply theme before render to prevent flash
const savedTheme = localStorage.getItem('litv-theme') || 'default'
document.documentElement.classList.add(`theme-${savedTheme}`)

const app = createApp(App)

// Register only the icons actually used (避免全量图标进入打包体积)
const icons = {
  AlarmClock, ArrowDown, ArrowRight, Bell, Brush, Cherry, CircleCheck, Clock,
  Connection, Delete, Document, DocumentAdd, DocumentCopy, Download, Expand, Fold,
  Folder, Grid, InfoFilled, Link, Lock, MagicStick, Monitor, Moon, Notebook,
  OfficeBuilding, Plus, Promotion, QuestionFilled, Rank, Reading, Refresh, Setting, Star,
  SuccessFilled, Sunny, Timer, TopRight, Upload, User, VideoPlay, View, Warning,
  WarningFilled
}
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

// Register permission directive
app.directive('permission', permissionDirective)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: undefined })

app.mount('#app')
