export function getPiePath(startAngle: number, endAngle: number): string {
  const sweep = endAngle - startAngle
  if (!Number.isFinite(sweep) || sweep <= 0) return ''
  const radius = 120
  const centerX = 150
  const centerY = 150
  const startRad = (startAngle - 90) * Math.PI / 180
  const startX = centerX + radius * Math.cos(startRad)
  const startY = centerY + radius * Math.sin(startRad)

  // A single SVG arc cannot draw a full circle with coincident endpoints.
  if (sweep >= 360) {
    const oppositeX = 2 * centerX - startX
    const oppositeY = 2 * centerY - startY
    return `M${startX} ${startY} A${radius} ${radius} 0 0 1 ${oppositeX} ${oppositeY} A${radius} ${radius} 0 0 1 ${startX} ${startY} Z`
  }
  const endRad = (endAngle - 90) * Math.PI / 180
  const endX = centerX + radius * Math.cos(endRad)
  const endY = centerY + radius * Math.sin(endRad)
  return `M${centerX} ${centerY} L${startX} ${startY} A${radius} ${radius} 0 ${sweep > 180 ? 1 : 0} 1 ${endX} ${endY} Z`
}

export function getStatusPercentage(value: number, total: number): number {
  return total > 0 ? Math.round((value / total) * 1000) / 10 : 0
}
