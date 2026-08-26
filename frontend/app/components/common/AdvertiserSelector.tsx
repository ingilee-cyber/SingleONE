"use client";

import { useEffect } from "react";
import { Autocomplete, CircularProgress, Stack, TextField, Typography } from "@mui/material";
import { useAdvertiserStore } from "@/lib/advertiserStore";
import type { AdvertiserOption } from "@/lib/advertiserApi";

/**
 * Global Header 우측의 전역 광고주 선택 UI. 데이터가 있는 광고주만 목록에 나온다(신규 광고주
 * 생성은 데이터 관리 화면의 업로드로만 가능 — 여기서는 만들지 않는다).
 */
export default function AdvertiserSelector() {
  const advertisers = useAdvertiserStore((s) => s.advertisers);
  const selectedAdvertiserId = useAdvertiserStore((s) => s.selectedAdvertiserId);
  const loading = useAdvertiserStore((s) => s.loading);
  const error = useAdvertiserStore((s) => s.error);
  const loaded = useAdvertiserStore((s) => s.loaded);
  const loadAdvertisers = useAdvertiserStore((s) => s.loadAdvertisers);
  const setSelectedAdvertiserId = useAdvertiserStore((s) => s.setSelectedAdvertiserId);

  useEffect(() => {
    loadAdvertisers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const selectedOption = advertisers.find((a) => a.advertiserId === selectedAdvertiserId) ?? null;

  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Autocomplete<AdvertiserOption>
        size="small"
        sx={{ width: 220 }}
        options={advertisers}
        loading={loading}
        value={selectedOption}
        getOptionLabel={(option) => option.advertiserId}
        isOptionEqualToValue={(option, value) => option.advertiserId === value.advertiserId}
        noOptionsText={loaded ? "등록된 광고주 없음" : "불러오는 중..."}
        onChange={(_, value) => value && setSelectedAdvertiserId(value.advertiserId)}
        renderInput={(params) => (
          <TextField
            {...params}
            label="광고주"
            InputProps={{
              ...params.InputProps,
              endAdornment: (
                <>
                  {loading ? <CircularProgress color="inherit" size={16} /> : null}
                  {params.InputProps.endAdornment}
                </>
              ),
            }}
          />
        )}
      />
      {error && (
        <Typography variant="caption" color="error">
          {error}
        </Typography>
      )}
    </Stack>
  );
}
