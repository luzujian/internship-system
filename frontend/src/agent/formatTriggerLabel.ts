/** 触发条件文案美化：将后端原始 triggerLabel 中的模板格式替换为自然语言。 */
export function formatTriggerLabel(label?: string): string {
  if (!label) return ''
  return label
    .replace(/(?:鉴定表截止日期|截止日期)?距今剩余天数为\s*(\d+)、(\d+)/g, '距离鉴定表截止日期还剩 $1 ~ $2 天')
    .replace(/(?:鉴定表截止日期|截止日期)?距今剩余天数为\s*(\d+)/g, '距离鉴定表截止日期还剩 $1 天')
    .replace(/距今剩余天数为\s*(\d+)、(\d+)/g, '还剩 $1 ~ $2 天')
    .replace(/距今剩余天数为\s*(\d+)/g, '还剩 $1 天')
    .replace(/逾期天数为\s*(\d+)、(\d+)/g, '已逾期 $1 ~ $2 天')
    .replace(/逾期天数为\s*(\d+)/g, '已逾期 $1 天')
}
