/** Dashboard/상세 화면 전반에서 재사용하는 숫자/퍼센트 표시 포맷. 반올림만 하고 재계산은 하지 않는다. */
export function fmt(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : Math.round(value).toLocaleString("ko-KR");
}

export function fmtPercent(value: number | null | undefined) {
  return value === null || value === undefined ? "-" : `${value.toFixed(1)}%`;
}
