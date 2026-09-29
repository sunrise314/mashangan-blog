import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
    base: '/admin/',
    plugins: [vue()],
    build: { outDir: '../src/main/resources/static/admin', emptyOutDir: true },
    server: {
        port: 5173,
        proxy: {
            '/api': { target: 'http://localhost:8090', changeOrigin: true },
            '/upload': { target: 'http://localhost:8090', changeOrigin: true },
        },
    },
});
