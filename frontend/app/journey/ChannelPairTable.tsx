"use client";

import { Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography } from "@mui/material";
import { fmt, fmtPercent } from "@/lib/format";
import type { ChannelPairRow } from "@/lib/journeyApi";

/**
 * PRD 9.7: 채널 페어는 빈도·구매매출·비중만 중립적으로 제공한다. "최적 조합"/"가장 효율적인
 * 조합" 같은 인과적 표현은 사용하지 않고, "가장 많이 관찰된 채널 페어"처럼만 표현한다.
 */
export default function ChannelPairTable({ rows }: { rows: ChannelPairRow[] }) {
  return (
    <Stack spacing={2}>
      <Typography variant="body2" color="text.secondary">
        가장 많이 관찰된 채널 페어부터 정렬해 표시합니다.
      </Typography>
      {rows.length === 0 ? (
        <Typography color="text.secondary">표시할 채널 페어가 없습니다.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>채널 페어</TableCell>
                <TableCell align="right">함께 등장한 구매 여정 수</TableCell>
                <TableCell align="right">구매매출</TableCell>
                <TableCell align="right">비중</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={`${row.channelA}-${row.channelB}`}>
                  <TableCell>{row.channelA} + {row.channelB}</TableCell>
                  <TableCell align="right">{row.journeyCount}</TableCell>
                  <TableCell align="right">{fmt(row.purchaseRevenue)}</TableCell>
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
