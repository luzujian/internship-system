/**
 * 将岗位薪资折算为 K/月区间，仅用于学生端薪资筛选。
 * 日薪按每月 21.75 个工作日、时薪按每天 8 小时估算。
 */
export const normalizeSalaryUnit = (unit) => {
  const supported = ['K/月', '元/天', '元/小时', '面议']
  return supported.includes(unit) ? unit : 'K/月'
}

export const getMonthlySalaryRangeK = (job) => {
  const unit = normalizeSalaryUnit(job.salaryUnit)
  if (unit === '面议') return null

  const parseSalaryValue = (value) => {
    if (value === null || value === undefined || value === '') return null
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  const min = parseSalaryValue(job.salaryMin)
  const max = parseSalaryValue(job.salaryMax)
  if (min === null && max === null) return null

  let factor = 1
  if (unit === '元/天') {
    factor = 21.75 / 1000
  } else if (unit === '元/小时') {
    factor = (8 * 21.75) / 1000
  }

  return {
    min: min !== null ? min * factor : 0,
    max: max !== null ? max * factor : Infinity
  }
}
