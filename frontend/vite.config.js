import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import Components from 'unplugin-vue-components/vite';
import { NaiveUiResolver } from 'unplugin-vue-components/resolvers';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    Components({
      dts: false,
      resolvers: [NaiveUiResolver()]
    })
  ],
  server: {
    port: 5173
  },
  test: {
    include: ['src/**/*.test.{js,ts}'],
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts']
  }
});


