#!/usr/bin/env bash
# Writes the README CI badge (same look as assets/badge/*.svg) for a job status.
# Usage: ci-badge.sh <success|failure|cancelled> <output.svg>
set -euo pipefail

case "$1" in
  success) TEXT="PASSING"; COLOR="#3DDC84" ;;
  failure) TEXT="FAILING"; COLOR="#E05D44" ;;
  *)       TEXT="CANCELLED"; COLOR="#9F9F9F" ;;
esac

LEFT=52
RIGHT=$(( ${#TEXT} * 9 + 22 ))
WIDTH=$(( LEFT + RIGHT ))
CENTER=$(( LEFT + RIGHT / 2 ))

cat > "$2" <<SVG
<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="28" viewBox="0 0 ${WIDTH} 28" role="img" aria-label="CI: ${TEXT}">
  <title>CI: ${TEXT}</title>
  <clipPath id="r"><rect width="${WIDTH}" height="28" rx="3"/></clipPath>
  <g clip-path="url(#r)">
    <rect width="${LEFT}" height="28" fill="#555"/>
    <rect x="${LEFT}" width="${RIGHT}" height="28" fill="${COLOR}"/>
  </g>
  <path transform="translate(10 7) scale(0.875)" fill="#fff" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="10" font-weight="700" letter-spacing="1.25">
    <text x="37" y="17.5">CI</text>
    <text x="${CENTER}" y="17.5">${TEXT}</text>
  </g>
</svg>
SVG
