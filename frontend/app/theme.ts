import { createTheme } from "@mui/material/styles";

// SingleONE 브랜드 로고(docs/design-references/logo.png)의 링 색상에서 추출한 브랜드 블루.
const BRAND_BLUE = "#3B63E0";
const BRAND_BLUE_DARK = "#2947B0";
const BRAND_BLUE_LIGHT = "#7C97EA";

const CARD_BORDER = "1px solid #E4E7EC";
const CARD_SHADOW = "0 1px 2px rgba(16, 24, 40, 0.04)";

const theme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: BRAND_BLUE,
      dark: BRAND_BLUE_DARK,
      light: BRAND_BLUE_LIGHT,
      contrastText: "#FFFFFF",
    },
    background: {
      default: "#F5F6F9",
      paper: "#FFFFFF",
    },
    text: {
      primary: "#16181D",
      secondary: "#667085",
    },
    divider: "#E4E7EC",
  },
  shape: {
    borderRadius: 10,
  },
  typography: {
    fontFamily: "var(--font-sans), -apple-system, BlinkMacSystemFont, sans-serif",
    h4: { fontWeight: 700, fontSize: "1.75rem" },
    h6: { fontWeight: 600, fontSize: "1.125rem" },
    subtitle1: { fontWeight: 600 },
    subtitle2: { fontWeight: 600 },
    button: { fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: "#F5F6F9",
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: "none",
          border: CARD_BORDER,
          boxShadow: CARD_SHADOW,
        },
        elevation0: {
          boxShadow: "none",
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: CARD_BORDER,
          boxShadow: CARD_SHADOW,
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          textTransform: "none",
          boxShadow: "none",
        },
        contained: {
          "&:hover": {
            boxShadow: "none",
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          fontWeight: 600,
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderColor: "#E4E7EC",
        },
        head: {
          backgroundColor: "#F9FAFB",
          color: "#667085",
          fontWeight: 600,
          fontSize: "0.75rem",
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        root: {
          minHeight: 40,
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: {
          textTransform: "none",
          fontWeight: 600,
          minHeight: 40,
        },
      },
    },
  },
});

export default theme;
