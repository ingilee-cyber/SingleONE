"use client";

import { Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from "@mui/material";
import { fmt } from "@/lib/format";
import type { TopPath } from "@/lib/journeyApi";

/** PRD 9.1/AC-55: 구매수 기준 Top 20 Path 테이블. */
export default function TopPathTable({ topPaths }: { topPaths: TopPath[] }) {
  if (topPaths.length === 0) {
    return <Typography color="text.secondary">표시할 Path가 없습니다.</Typography>;
  }
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Path</TableCell>
            <TableCell align="right">구매수</TableCell>
            <TableCell align="right">구매매출</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {topPaths.map((path, index) => (
            <TableRow key={index}>
              <TableCell>{[...path.channels, "구매"].join(" → ")}</TableCell>
              <TableCell align="right">{path.purchaseCount}</TableCell>
              <TableCell align="right">{fmt(path.purchaseRevenue)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
