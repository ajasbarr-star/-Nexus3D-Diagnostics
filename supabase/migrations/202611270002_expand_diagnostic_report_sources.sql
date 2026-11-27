-- Expand the accepted diagnostic report source taxonomy without touching existing reports.
-- Errors and warnings remain severity values, while source identifies the originating RDS tool.
alter table public.diagnostic_reports
    drop constraint if exists diagnostic_reports_source_check;

alter table public.diagnostic_reports
    add constraint diagnostic_reports_source_check
    check (source in (
        'RDS_LOGCAT',
        'RIT_TRACE',
        'RUNTIME',
        'WEBGL_SHADER',
        'SHADOW_MAPS',
        'MATERIALS_TEXTURES',
        'DFI_CORE',
        'MESH_DFI',
        'ROOT_CAUSE',
        'SOURCE_AUDIT',
        'REPORTS'
    ));
