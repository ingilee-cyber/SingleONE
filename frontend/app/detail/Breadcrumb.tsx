"use client";

import { Breadcrumbs, Link as MuiLink, Typography } from "@mui/material";
import NextLink from "next/link";

export interface BreadcrumbItem {
  label: string;
  href?: string;
}

interface BreadcrumbProps {
  items: BreadcrumbItem[];
}

/** PRD 7.1: 계층별 상세 화면 공용 Breadcrumb. 각 페이지가 이미 가져온 이름으로 구성해서 넘긴다. */
export default function Breadcrumb({ items }: BreadcrumbProps) {
  return (
    <Breadcrumbs>
      {items.map((item, index) =>
        item.href ? (
          <MuiLink key={index} component={NextLink} href={item.href}>
            {item.label}
          </MuiLink>
        ) : (
          <Typography key={index} color="text.primary">
            {item.label}
          </Typography>
        ),
      )}
    </Breadcrumbs>
  );
}
