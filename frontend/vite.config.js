import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Default Vite + React config (Module 7, Step 1: `npm create vite@latest -- --template react`).
export default defineConfig({
  plugins: [react()],
});
