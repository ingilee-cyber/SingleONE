"use client";

import { useMemo, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Tooltip,
  Typography,
} from "@mui/material";
import { INDEX_STATUS_LABEL, type MediaIndexResult } from "@/lib/dashboardApi";
import { fmt, fmtPercent } from "@/lib/format";

type SortKey =
  | "media"
  | "cost"
  | "impressions"
  | "clicks"
  | "rawPurchases"
  | "singleOnePurchases"
  | "rawRevenue"
  | "singleOneRevenue"
  | "rawRoas"
  | "singleOneRoas";

function getValue(row: MediaIndexResult, key: SortKey): number | string {
  switch (key) {
    case "media":
      return row.media;
    case "cost":
      return row.rawTotals?.cost ?? -1;
    case "impressions":
      return row.rawTotals?.impressions ?? -1;
    case "clicks":
      return row.rawTotals?.clicks ?? -1;
    case "rawPurchases":
      return row.rawTotals?.rawPurchases ?? -1;
    case "singleOnePurchases":
      return row.singleOnePerformance?.singleOnePurchases ?? -1;
    case "rawRevenue":
      return row.rawTotals?.rawRevenue ?? -1;
    case "singleOneRevenue":
      return row.singleOnePerformance?.singleOneRevenue ?? -1;
    case "rawRoas":
      return row.rawPerformance?.roas ?? -1;
    case "singleOneRoas":
      return row.singleOnePerformance?.roas ?? -1;
    default:
      return -1;
  }
}

const COLUMNS: { key: SortKey; label: string; info?: string }[] = [
  { key: "media", label: "매체" },
  { key: "cost", label: "Cost" },
  { key: "impressions", label: "Impressions" },
  { key: "clicks", label: "Clicks" },
  { key: "rawPurchases", label: "원본 구매" },
  { key: "singleOnePurchases", label: "SingleONE 구매", info: "자체 내부 전환 기준입니다." },
  { key: "rawRevenue", label: "원본 구매매출" },
  { key: "singleOneRevenue", label: "SingleONE 구매매출" },
  { key: "rawRoas", label: "원본 ROAS" },
  { key: "singleOneRoas", label: "SingleONE ROAS" },
];

interface PerformanceTableProps {
  results: MediaIndexResult[];
}

/** PRD 6.3 항목 3: 원본+SingleONE 효율 성과 테이블, 컬럼별 정렬(값 재계산 없이 정렬만 한다). */
export default function PerformanceTable({ results }: PerformanceTableProps) {
  const [sortKey, setSortKey] = useState<SortKey>("media");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");

  const sorted = useMemo(() => {
    return [...results].sort((a, b) => {
      const av = getValue(a, sortKey);
      const bv = getValue(b, sortKey);
      const cmp = av < bv ? -1 : av > bv ? 1 : 0;
      return sortDir === "asc" ? cmp : -cmp;
    });
  }, [results, sortKey, sortDir]);

  const handleSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            {COLUMNS.map((col) => (
              <TableCell key={col.key}>
                <TableSortLabel active={sortKey === col.key} direction={sortDir} onClick={() => handleSort(col.key)}>
                  {col.label}
                </TableSortLabel>
                {col.info && (
                  <Tooltip title={col.info}>
                    <Typography component="span" color="text.secondary" sx={{ ml: 0.5 }}>
                      ⓘ
                    </Typography>
                  </Tooltip>
                )}
              </TableCell>
            ))}
            <TableCell>상태</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sorted.map((row) => (
            <TableRow key={row.media}>
              <TableCell>{row.media}</TableCell>
              <TableCell>{fmt(row.rawTotals?.cost)}</TableCell>
              <TableCell>{fmt(row.rawTotals?.impressions)}</TableCell>
              <TableCell>{fmt(row.rawTotals?.clicks)}</TableCell>
              <TableCell>{fmt(row.rawTotals?.rawPurchases)}</TableCell>
              <TableCell>{fmt(row.singleOnePerformance?.singleOnePurchases)}</TableCell>
              <TableCell>{fmt(row.rawTotals?.rawRevenue)}</TableCell>
              <TableCell>{fmt(row.singleOnePerformance?.singleOneRevenue)}</TableCell>
              <TableCell>{fmtPercent(row.rawPerformance?.roas)}</TableCell>
              <TableCell>{fmtPercent(row.singleOnePerformance?.roas)}</TableCell>
              <TableCell>{INDEX_STATUS_LABEL[row.status]}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
