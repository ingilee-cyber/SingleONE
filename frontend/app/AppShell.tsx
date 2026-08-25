"use client";

import { Box, Stack, Typography } from "@mui/material";
import NextLink from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode, SVGProps } from "react";

const SIDEBAR_WIDTH = 88;
const TOPBAR_HEIGHT = 56;

function LogoMark(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 32 32" width="24" height="24" fill="none" {...props}>
      <ellipse cx="16" cy="9" rx="10" ry="5.5" stroke="#3B63E0" strokeWidth="3" />
      <ellipse cx="16" cy="16" rx="10" ry="5.5" stroke="#2947B0" strokeWidth="3" />
      <ellipse cx="16" cy="23" rx="10" ry="5.5" stroke="#3B63E0" strokeWidth="3" />
    </svg>
  );
}

function HomeIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M4 11.5 12 4l8 7.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M6 10v9h12v-9" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function UploadIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <ellipse cx="12" cy="6" rx="7" ry="2.5" />
      <path d="M5 6v6c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V6" />
      <path d="M5 12v6c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-6" />
    </svg>
  );
}

function ProjectIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M3.5 7a1 1 0 0 1 1-1h4l2 2h9a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-15a1 1 0 0 1-1-1z" strokeLinejoin="round" />
    </svg>
  );
}

function DashboardIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M4 20V10" strokeLinecap="round" />
      <path d="M10 20V4" strokeLinecap="round" />
      <path d="M16 20v-7" strokeLinecap="round" />
      <path d="M4 20h16" strokeLinecap="round" />
    </svg>
  );
}

function JourneyIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <circle cx="5" cy="6" r="2" />
      <circle cx="5" cy="18" r="2" />
      <circle cx="19" cy="12" r="2" />
      <path d="M7 6h6a4 4 0 0 1 4 4v0" strokeLinecap="round" />
      <path d="M7 18h6a4 4 0 0 0 4-4v0" strokeLinecap="round" />
    </svg>
  );
}

function SimulationIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8" {...props}>
      <path d="M9 18h6" strokeLinecap="round" />
      <path d="M10 21h4" strokeLinecap="round" />
      <path d="M12 3a6 6 0 0 0-3.5 10.9c.6.4.9 1 .9 1.7V16h5.2v-.4c0-.7.3-1.3.9-1.7A6 6 0 0 0 12 3z" strokeLinejoin="round" />
    </svg>
  );
}

const NAV_ITEMS: { href: string; label: string; icon: (props: SVGProps<SVGSVGElement>) => ReactNode }[] = [
  { href: "/", label: "홈", icon: HomeIcon },
  { href: "/uploads", label: "데이터 관리", icon: UploadIcon },
  { href: "/projects", label: "프로젝트", icon: ProjectIcon },
  { href: "/dashboard", label: "대시보드", icon: DashboardIcon },
  { href: "/journey", label: "Journey", icon: JourneyIcon },
  { href: "/simulation", label: "Simulation", icon: SimulationIcon },
];

export default function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  return (
    <Box sx={{ minHeight: "100vh", bgcolor: "#F5F6F9" }}>
      <Stack
        direction="row"
        alignItems="center"
        spacing={1}
        sx={{
          height: TOPBAR_HEIGHT,
          px: 2.5,
          bgcolor: "#FFFFFF",
          borderBottom: "1px solid #E4E7EC",
          position: "sticky",
          top: 0,
          zIndex: 10,
        }}
      >
        <LogoMark />
        <Typography variant="subtitle1" component="span" sx={{ fontWeight: 700 }}>
          SingleONE
        </Typography>
      </Stack>

      <Stack direction="row" alignItems="stretch">
        <Stack
          component="nav"
          spacing={0.5}
          sx={{
            width: SIDEBAR_WIDTH,
            flexShrink: 0,
            py: 2,
            bgcolor: "#FFFFFF",
            borderRight: "1px solid #E4E7EC",
            minHeight: `calc(100vh - ${TOPBAR_HEIGHT}px)`,
          }}
        >
          {NAV_ITEMS.map((item) => {
            const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
            const Icon = item.icon;
            return (
              <NextLink key={item.href} href={item.href} style={{ textDecoration: "none" }}>
                <Stack
                  alignItems="center"
                  spacing={0.5}
                  sx={{
                    mx: 1,
                    py: 1,
                    borderRadius: 1.5,
                    color: active ? "primary.main" : "text.secondary",
                    bgcolor: active ? "rgba(59, 99, 224, 0.08)" : "transparent",
                    "&:hover": { bgcolor: active ? "rgba(59, 99, 224, 0.12)" : "rgba(16, 24, 40, 0.04)" },
                  }}
                >
                  <Icon />
                  <Typography variant="caption" sx={{ fontSize: "0.65rem", fontWeight: 600, lineHeight: 1.2, textAlign: "center" }}>
                    {item.label}
                  </Typography>
                </Stack>
              </NextLink>
            );
          })}
        </Stack>

        <Box component="main" sx={{ flex: 1, minWidth: 0 }}>
          {children}
        </Box>
      </Stack>
    </Box>
  );
}
