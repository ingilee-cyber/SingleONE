"use client";

import { useEffect, useState } from "react";
import { Box, Button, Checkbox, FormControlLabel, Menu, Stack } from "@mui/material";
import type { ProjectTotals } from "@/lib/dashboardApi";
import { fmt as formatNumber, fmtPercent as formatPercent } from "@/lib/format";
import StatCard from "@/app/components/common/StatCard";

type KpiKey = "cost" | "impressions" | "clicks" | "purchases" | "revenue" | "roas";

const KPI_KEYS: KpiKey[] = ["cost", "impressions", "clicks", "purchases", "revenue", "roas"];
const KPI_LABEL: Record<KpiKey, string> = {
  cost: "Cost",
  impressions: "Impressions",
  clicks: "Clicks",
  purchases: "Purchases",
  revenue: "Purchase Revenue",
  roas: "ROAS",
};

const STORAGE_KEY = "singleone.dashboard.kpiVisibility";

function loadVisible(): KpiKey[] {
  if (typeof window === "undefined") {
    return KPI_KEYS;
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return KPI_KEYS;
    }
    const parsed: string[] = JSON.parse(raw);
    return KPI_KEYS.filter((key) => parsed.includes(key));
  } catch {
    return KPI_KEYS;
  }
}

function changeLabel(current: number, previous: number | undefined) {
  if (previous === undefined || previous === 0) {
    return null;
  }
  const percent = ((current - previous) / previous) * 100;
  const sign = percent >= 0 ? "▲" : "▼";
  return `${sign} ${Math.abs(percent).toFixed(1)}% (이전 기간 대비)`;
}

interface KpiCardsProps {
  totals: ProjectTotals;
  previousTotals?: ProjectTotals;
  comparePrevious: boolean;
}

export default function KpiCards({ totals, previousTotals, comparePrevious }: KpiCardsProps) {
  const [visible, setVisible] = useState<KpiKey[]>(KPI_KEYS);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  useEffect(() => {
    setVisible(loadVisible());
  }, []);

  const toggle = (key: KpiKey) => {
    setVisible((prev) => {
      const next = prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key];
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      return next;
    });
  };

  const cards: Record<KpiKey, { title: string; primary: string; secondary?: string; info?: string; change?: string | null }> = {
    cost: {
      title: "Cost",
      primary: formatNumber(totals.cost),
      change: comparePrevious ? changeLabel(totals.cost, previousTotals?.cost) : null,
    },
    impressions: {
      title: "Impressions",
      primary: formatNumber(totals.impressions),
      change: comparePrevious ? changeLabel(totals.impressions, previousTotals?.impressions) : null,
    },
    clicks: {
      title: "Clicks",
      primary: formatNumber(totals.clicks),
      change: comparePrevious ? changeLabel(totals.clicks, previousTotals?.clicks) : null,
    },
    purchases: {
      title: "Purchases",
      primary: `원본 ${formatNumber(totals.rawPurchases)}`,
      secondary: `SingleONE ${formatNumber(totals.singleOnePurchases)}`,
      info: "자체 내부 전환 기준입니다.",
      change: comparePrevious ? changeLabel(totals.rawPurchases, previousTotals?.rawPurchases) : null,
    },
    revenue: {
      title: "Purchase Revenue",
      primary: `원본 ${formatNumber(totals.rawRevenue)}`,
      secondary: `SingleONE ${formatNumber(totals.singleOneRevenue)}`,
      change: comparePrevious ? changeLabel(totals.rawRevenue, previousTotals?.rawRevenue) : null,
    },
    roas: {
      title: "ROAS",
      primary: `원본 ${formatPercent(totals.rawRoas)}`,
      secondary: `SingleONE ${formatPercent(totals.singleOneRoas)}`,
    },
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="flex-end" sx={{ mb: 1 }}>
        <Button size="small" onClick={(e) => setMenuAnchor(e.currentTarget)}>
          표시 항목
        </Button>
        <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
          {KPI_KEYS.map((key) => (
            <Box key={key} sx={{ px: 2 }}>
              <FormControlLabel
                control={<Checkbox checked={visible.includes(key)} onChange={() => toggle(key)} />}
                label={KPI_LABEL[key]}
              />
            </Box>
          ))}
        </Menu>
      </Stack>
      <Box
        data-testid="kpi-cards"
        sx={{
          display: "grid",
          gap: 2,
          gridTemplateColumns: {
            xs: "1fr",
            sm: "repeat(2, 1fr)",
            md: "repeat(3, 1fr)",
            lg: `repeat(${KPI_KEYS.filter((key) => visible.includes(key)).length}, 1fr)`,
          },
        }}
      >
        {KPI_KEYS.filter((key) => visible.includes(key)).map((key) => {
          const card = cards[key];
          return (
            <StatCard
              key={key}
              label={card.title}
              primary={card.primary}
              secondary={card.secondary}
              info={card.info}
              change={card.change}
            />
          );
        })}
      </Box>
    </Box>
  );
}
