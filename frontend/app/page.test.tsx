import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Home from "./page";
import apiClient from "@/lib/apiClient";

vi.mock("@/lib/apiClient", () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: { status: "UP" } })),
  },
}));

describe("Home", () => {
  it("renders the SingleONE title and backend status", async () => {
    render(<Home />);

    expect(screen.getByRole("heading", { name: "SingleONE" })).toBeInTheDocument();
    expect(await screen.findByText("연결됨")).toBeInTheDocument();
    expect(apiClient.get).toHaveBeenCalledWith("/actuator/health");
  });
});
