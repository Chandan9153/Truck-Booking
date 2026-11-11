// PostCSS config — Tailwind CSS v3 needs this file to turn the
// `@tailwind base/components/utilities` lines in src/index.css into
// real CSS. It was missing, so the built stylesheet was shipped
// unprocessed and the live site rendered with no styling at all.
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
