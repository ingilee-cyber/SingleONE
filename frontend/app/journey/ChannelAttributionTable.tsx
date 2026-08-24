"use client";

import { Alert, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from "@mui/material";
import { fmt, fmtPercent } from "@/lib/format";
import type { ChannelAttributionRow } from "@/lib/journeyApi";

/**
 * PRD 9.6: Linear Attribution 결과. "SingleONE 기여 구매"라는 표현은 사용하지 않으며,
 * Dashboard의 SingleONE 성과와 집계값이 다를 수 있음을 안내한다.
 */
export default function ChannelAttributionTable({ rows }: { rows: ChannelAttributionRow[] }) {
  return (
    <Stack spacing={2}>
      <Alert severity="info">
        Journey & Attribution 분석은 이벤트 데이터를 기준으로 하며 Dashboard의 SingleONE 성과와 집계값이 다를 수 있습니다.
      </Alert>
      {rows.length === 0 ? (
        <Typography color="text.secondary">표시할 채널이 없습니다.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>채널</TableCell>
                <TableCell align="right">기여 구매</TableCell>
                <TableCell align="right">기여 구매매출</TableCell>
                <TableCell align="right">기여 비중</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.channel}>
                  <TableCell>{row.channel}</TableCell>
                  <TableCell align="right">{row.attributedPurchases.toFixed(2)}</TableCell>
                  <TableCell align="right">{fmt(row.attributedRevenue)}</TableCell>
                  <TableCell align="right">{fmtPercent(row.sharePercent)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Stack>
  );
}
