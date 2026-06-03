#!/usr/bin/env bash
set -euo pipefail

contact_email="${1:-${PRIVACY_CONTACT_EMAIL:-}}"
output_dir="${2:-pages-dist}"

if [[ -z "${contact_email}" ]]; then
  echo "Missing contact email. Set PRIVACY_CONTACT_EMAIL before building the privacy pages." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_root="${repo_root}/${output_dir}"
privacy_root="${output_root}/privacy"
english_root="${privacy_root}/en"

rm -rf "${output_root}"
mkdir -p "${privacy_root}" "${english_root}"

html_escape() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</&lt;}"
  value="${value//>/&gt;}"
  value="${value//\"/&quot;}"
  value="${value//\'/&#39;}"
  printf '%s' "$value"
}

encoded_email="$(html_escape "${contact_email}")"

render_template() {
  local source="$1"
  local target="$2"
  sed "s|__PRIVACY_CONTACT_EMAIL__|${encoded_email}|g" "${source}" > "${target}"
}

render_template "${repo_root}/assets/privacy.html" "${privacy_root}/index.html"
render_template "${repo_root}/assets/privacy.en.html" "${english_root}/index.html"

cat > "${output_root}/index.html" <<'EOF'
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="description" content="Unlock Capture on Google Play and privacy pages in German and English." />
    <title>Unlock Capture</title>
    <style>
      :root {
        color-scheme: light dark;
        --border: rgba(127, 127, 127, 0.35);
        --muted-bg: rgba(127, 127, 127, 0.07);
      }
      * { box-sizing: border-box; }
      body {
        font-family: system-ui, -apple-system, "Segoe UI", Roboto, Ubuntu, Cantarell, "Noto Sans", sans-serif;
        line-height: 1.6;
        margin: 0;
        padding: 24px;
      }
      main {
        max-width: 760px;
        margin: 0 auto;
      }
      .card {
        border: 1px solid var(--border);
        border-radius: 16px;
        padding: 20px;
        background: var(--muted-bg);
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        margin-top: 20px;
      }
      .button {
        display: inline-block;
        padding: 10px 14px;
        border-radius: 10px;
        border: 1px solid var(--border);
        text-decoration: none;
        color: inherit;
        font-weight: 600;
      }
    </style>
  </head>
  <body>
    <main>
      <div class="card">
        <h1>Unlock Capture</h1>
        <p>Android app for transparent unlock event capture with optional front camera recording.</p>
        <div class="actions">
          <a class="button" href="https://play.google.com/store/apps/details?id=de.eberhardt.unlockcapture">Google Play Store</a>
          <a class="button" href="./privacy/">Privacy Policy (DE)</a>
          <a class="button" href="./privacy/en/">Privacy Policy (EN)</a>
        </div>
      </div>
    </main>
  </body>
</html>
EOF

: > "${output_root}/.nojekyll"
