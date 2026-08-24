import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ChildEntityTable from "./ChildEntityTable";
import type { EntityPage, EntityPerformance } from "@/lib/detailApi";

const rows: EntityPerformance[] = [
  {
    id: "camp-1",
    name: "여름캠페인",
    rawTotals: { media: "META", impressions: 1000, clicks: 100, cost: 500000, rawPurchases: 10, rawRevenue: 1000000, operatingDays: 7 },
    rawPerformance: { cpa: 50000, roas: 200 },
    singleOnePerformance: { media: "META", singleOnePurchases: 6.5, singleOneRevenue: 650000, cpa: 76923, roas: 130 },
  },
];

const page: EntityPage = { content: rows, totalElements: 1, totalPages: 1, number: 0, size: 50 };

describe("ChildEntityTable", () => {
  let fetchPage: (params: { search?: string; page: number; size: number; sort: string }) => Promise<EntityPage>;
  let onRowClick: (row: EntityPerformance) => void;

  beforeEach(() => {
    fetchPage = vi.fn().mockResolvedValue(page);
    onRowClick = vi.fn();
  });

  it("fetches the first page with default sort on mount and renders rows", async () => {
    render(<ChildEntityTable title="캠페인" fetchPage={fetchPage} onRowClick={onRowClick} />);

    await waitFor(() => {
      expect(fetchPage).toHaveBeenCalledWith({ search: undefined, page: 0, size: 50, sort: "name,asc" });
    });
    expect(await screen.findByText("여름캠페인")).toBeInTheDocument();
  });

  it("searches by name/id when the 검색 button is clicked", async () => {
    render(<ChildEntityTable title="캠페인" fetchPage={fetchPage} onRowClick={onRowClick} />);
    await screen.findByText("여름캠페인");

    fireEvent.change(screen.getByRole("textbox", { name: "캠페인 이름/ID 검색" }), { target: { value: "여름" } });
    fireEvent.click(screen.getByRole("button", { name: "검색" }));

    await waitFor(() => {
      expect(fetchPage).toHaveBeenLastCalledWith({ search: "여름", page: 0, size: 50, sort: "name,asc" });
    });
  });

  it("toggles sort direction when a column header is clicked", async () => {
    render(<ChildEntityTable title="캠페인" fetchPage={fetchPage} onRowClick={onRowClick} />);
    await screen.findByText("여름캠페인");

    fireEvent.click(screen.getByRole("button", { name: "Cost" }));
    await waitFor(() => {
      expect(fetchPage).toHaveBeenLastCalledWith({ search: undefined, page: 0, size: 50, sort: "cost,asc" });
    });
  });

  it("navigates when a row is clicked", async () => {
    render(<ChildEntityTable title="캠페인" fetchPage={fetchPage} onRowClick={onRowClick} />);
    fireEvent.click(await screen.findByText("여름캠페인"));

    expect(onRowClick).toHaveBeenCalledWith(rows[0]);
  });

  // AC-54: 페이지 크기는 최대 200까지만 선택 가능해야 한다(그 이상 옵션이 없어야 함).
  it("AC-54: offers 200 as the maximum selectable page size and requests it when chosen", async () => {
    render(<ChildEntityTable title="캠페인" fetchPage={fetchPage} onRowClick={onRowClick} />);
    await screen.findByText("여름캠페인");

    fireEvent.mouseDown(screen.getByRole("combobox"));
    const options = screen.getAllByRole("option").map((el) => el.textContent);
    expect(options).toEqual(["20", "50", "100", "200"]);

    fireEvent.click(screen.getByRole("option", { name: "200" }));
    await waitFor(() => {
      expect(fetchPage).toHaveBeenLastCalledWith({ search: undefined, page: 0, size: 200, sort: "name,asc" });
    });
  });
});
