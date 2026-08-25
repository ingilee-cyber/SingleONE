"use client";

import { useEffect, useState } from "react";
import { Box, Button, Chip, Container, Stack, Typography } from "@mui/material";
import NextLink from "next/link";
import apiClient from "@/lib/apiClient";

const QUICK_LINKS = [
  { href: "/uploads", label: "데이터 관리 화면으로 이동" },
  { href: "/projects", label: "프로젝트 화면으로 이동" },
  { href: "/dashboard", label: "Dashboard 화면으로 이동" },
  { href: "/journey", label: "Journey & Attribution 화면으로 이동" },
  { href: "/simulation", label: "Media Planning Simulation 화면으로 이동" },
];

type BackendStatus = "checking" | "connected" | "disconnected";

export default function Home() {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>("checking");

  useEffect(() => {
    let cancelled = false;

    apiClient
      .get("/actuator/health")
      .then((response) => {
        if (!cancelled) {
          setBackendStatus(response.data?.status === "UP" ? "connected" : "disconnected");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setBackendStatus("disconnected");
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <Container maxWidth="sm">
      <Box sx={{ py: 8 }}>
        <Stack spacing={2}>
          <Typography variant="h3" component="h1">
            SingleONE
          </Typography>
          <Typography variant="body1" color="text.secondary">
            Backend/Frontend/DB 기본 실행환경 구축 단계입니다. 이 화면은 실제 제품 화면이 아니라
            Backend 연결 확인용 임시 화면입니다.
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center">
            <Typography variant="body2">Backend 상태:</Typography>
            <Chip
              label={
                backendStatus === "checking"
                  ? "확인 중..."
                  : backendStatus === "connected"
                    ? "연결됨"
                    : "연결 안 됨"
              }
              color={
                backendStatus === "connected"
                  ? "success"
                  : backendStatus === "disconnected"
                    ? "error"
                    : "default"
              }
              size="small"
            />
          </Stack>
          <Stack spacing={1}>
            {QUICK_LINKS.map((link) => (
              <Button
                key={link.href}
                component={NextLink}
                href={link.href}
                variant="outlined"
                sx={{ justifyContent: "flex-start" }}
              >
                {link.label}
              </Button>
            ))}
          </Stack>
        </Stack>
      </Box>
    </Container>
  );
}
