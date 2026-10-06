type Row = Record<string, unknown>;
type Point = [number, number];

/** Public geometry preserves shape and gaps but carries no geographic origin or scale. */
export function publicRoute(mode: unknown, segments: Row[], route: Row[]) {
  const parts: Point[][] = [];
  const byId = new Map<unknown, Row & { index: number }>(segments.map((s, index) => [s.id, { ...s, index }]));
  let lastId = 0, lastIndex = -1, lastTime = -1, longitude: number | null = null;
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  const meanLatitude = route.reduce((sum, p) => sum + Number(p.latitude), 0) / (route.length || 1);
  const factor = Math.max(0.01, Math.cos(meanLatitude * Math.PI / 180));
  for (const p of route) {
    const segment = byId.get(p.segmentId);
    const lat = Number(p.latitude), lon = Number(p.longitude), time = Number(p.monotonicMs), id = Number(p.id);
    if (mode !== "OUTDOOR" || !segment || segment.source !== "GPS" || !Number.isSafeInteger(id) || id <= lastId ||
        !Number.isSafeInteger(time) || time < 0 || time < Number(segment.startedMonotonicMs) || time > Number(segment.endedMonotonicMs) ||
        segment.index < lastIndex || (segment.index === lastIndex && time <= lastTime) ||
        !Number.isFinite(lat) || Math.abs(lat) > 90 || !Number.isFinite(lon) || Math.abs(lon) > 180) throw new Error("Invalid route");
    if (segment.index !== lastIndex || time - lastTime > 10000) parts.push([]);
    longitude = lon + (longitude === null ? 0 : 360 * Math.round((longitude - lon) / 360));
    const x = longitude * factor, y = -lat;
    parts.at(-1)!.push([x, y]);
    minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
    lastId = id; lastIndex = segment.index; lastTime = time;
  }
  const span = Math.max(maxX - minX, maxY - minY) || 1;
  return { normalized: true, pointCount: route.length,
    parts: parts.map(part => part.map(([x, y]) => [
      Number(((x - (minX + maxX) / 2) / span).toFixed(6)),
      Number(((y - (minY + maxY) / 2) / span).toFixed(6)),
    ])),
    note: !route.length ? (mode === "INDOOR" ? "Indoor run · no GPS route." : "No GPS route was recorded for this run.") :
      route.length === 1 ? "One GPS point recorded; there is no route line to draw." :
        "Recorded GPS only. Pauses and missing GPS sections are left unconnected.",
  };
}
