# Keyxif Web

Keyxif Web is the browser version of Keyxif. It is a static HTML/CSS/JavaScript app that runs independently from the Android project.

## Commands

```bash
npm install
npm run dev
npm run build
npm run preview
```

`npm run build` copies the deployable static files into `web/dist`.

## Cloudflare Pages

Recommended settings for Cloudflare Pages Git integration:

- Root directory: `web`
- Build command: `npm run build`
- Build output directory: `dist`
- Production branch: `main`

The GitHub Actions workflow can also deploy with Wrangler when these repository secrets are set:

- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_PAGES_PROJECT_NAME`

Use Git integration for the simplest setup. Use the workflow only when you want GitHub Actions to own the deployment.
