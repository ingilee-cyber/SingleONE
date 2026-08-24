"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Button,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TableSortLabel,
  TextField,
} from "@mui/material";
import { fmt } from "@/lib/format";
import type { EntityPage, EntityPerformance } from "@/lib/detailApi";

type SortKey =
  | "name"
  | "id"
  | "cost"
  | "impressions"
  | "clicks"
  | "rawPurchases"
  | "singleOnePurchases"
  | "rawRevenue"
  | "singleOneRevenue";

const COLUMNS: { key: SortKey; label: string }[] = [
  { key: "name", label: "이름" },
  { key: "id", label: "ID" },
  { key: "cost", label: "Cost" },
  { key: "impressions", label: "Impressions" },
  { key: "clicks", label: "Clicks" },
  { key: "rawPurchases", label: "원본 구매" },
  { key: "singleOnePurchases", label: "SingleONE 구매" },
  { key: "rawRevenue", label: "원본 매출" },
  { key: "singleOneRevenue", label: "SingleONE 매출" },
];

interface ChildEntityTableProps {
  title: string;
  fetchPage: (params: { search?: string; page: number; size: number; sort: string }) => Promise<EntityPage>;
  onRowClick: (row: EntityPerformance) => void;
}

/**
 * PRD 7장 하위 목록 공용 컴포넌트(캠페인/광고그룹/광고 목록 3곳에서 재사용). 검색·정렬·페이지 변경마다
 * Backend를 다시 호출한다(서버 페이지네이션/검색/정렬 — 클라이언트에서 값을 다시 계산하지 않는다).
 */
export default function ChildEntityTable({ title, fetchPage, onRowClick }: ChildEntityTableProps) {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("name");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("asc");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(50);
  const [data, setData] = useState<EntityPage | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    fetchPage({ search: search || undefined, page, size, sort: `${sortKey},${sortDir}` })
      .then((result) => {
        setData(result);
        setError(null);
      })
      .catch(() => setError("목록을 불러오지 못했습니다."));
  }, [fetchPage, search, page, size, sortKey, sortDir]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const handleSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDir((prev) => (prev === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
    setPage(0);
  };

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={2}>
        <TextField
          label={`${title} 이름/ID 검색`}
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          size="small"
          fullWidth
        />
        <Button
          onClick={() => {
            setPage(0);
            setSearch(searchInput);
          }}
        >
          검색
        </Button>
      </Stack>
      {error && <Alert severity="error">{error}</Alert>}
      <TableContainer>
        <Table size="small">
          <TableHead>
            <TableRow>
              {COLUMNS.map((col) => (
                <TableCell key={col.key}>
                  <TableSortLabel active={sortKey === col.key} direction={sortDir} onClick={() => handleSort(col.key)}>
                    {col.label}
                  </TableSortLabel>
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {(data?.content ?? []).map((row) => (
              <TableRow key={row.id} hover sx={{ cursor: "pointer" }} onClick={() => onRowClick(row)}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.id}</TableCell>
                <TableCell>{fmt(row.rawTotals.cost)}</TableCell>
                <TableCell>{fmt(row.rawTotals.impressions)}</TableCell>
                <TableCell>{fmt(row.rawTotals.clicks)}</TableCell>
                <TableCell>{fmt(row.rawTotals.rawPurchases)}</TableCell>
                <TableCell>{fmt(row.singleOnePerformance.singleOnePurchases)}</TableCell>
                <TableCell>{fmt(row.rawTotals.rawRevenue)}</TableCell>
                <TableCell>{fmt(row.singleOnePerformance.singleOneRevenue)}</TableCell>
              </TableRow>
            ))}
            {data?.content.length === 0 && (
              <TableRow>
                <TableCell colSpan={COLUMNS.length}>조건에 맞는 항목이 없습니다.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={data?.totalElements ?? 0}
        page={page}
        onPageChange={(_, newPage) => setPage(newPage)}
        rowsPerPage={size}
        onRowsPerPageChange={(e) => {
          setSize(Number(e.target.value));
          setPage(0);
        }}
        rowsPerPageOptions={[20, 50, 100, 200]}
      />
    </Stack>
  );
}
