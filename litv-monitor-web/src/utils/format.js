import dayjs from 'dayjs'
import utc from 'dayjs/plugin/utc'
import timezone from 'dayjs/plugin/timezone'

dayjs.extend(utc)
dayjs.extend(timezone)

const BEIJING_TZ = 'Asia/Shanghai'

export function formatTime(date, fmt = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return '-'
  return dayjs(date).tz(BEIJING_TZ).format(fmt)
}
