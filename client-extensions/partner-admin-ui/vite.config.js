import react from '@vitejs/plugin-react';
import {defineConfig} from 'vite';

// Output matches client-extension.yaml: build/static/js/main.*.js and
// build/static/css/main.*.css. React is bundled, not taken from the portal.

export default defineConfig({
	build: {
		outDir: 'build/static',
		rollupOptions: {
			input: 'src/index.jsx',
			output: {
				assetFileNames: 'css/main.[hash][extname]',
				chunkFileNames: 'js/[name].[hash].js',
				entryFileNames: 'js/main.[hash].js',
			},
			preserveEntrySignatures: 'exports-only',
		},
	},
	define: {
		'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'production'),
	},
	plugins: [react()],
	test: {
		environment: 'jsdom',
		globals: true,
		setupFiles: ['src/test/setup.js'],
	},
});
