/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        /* Institutional navy — sidebar, headers, primary surfaces */
        navy: {
          950: '#071523',
          900: '#0B1F33',
          800: '#16324F',
          700: '#1D4066',
          600: '#25507E',
          100: '#DDE5EE',
        },
        /* Deep blue accent — actions, active states, primary data series */
        brand: {
          50: '#EFF4FF',
          100: '#DBE6FE',
          200: '#BDD1FD',
          400: '#5B8DEF',
          500: '#3B76EE',
          600: '#2563EB',
          700: '#1D4ED8',
          800: '#1E40AF',
        },
        /* Neutral system */
        canvas: '#F6F8FB',
        surface: '#FFFFFF',
        ink: {
          DEFAULT: '#172033',
          900: '#0F1729',
          800: '#172033',
          700: '#344054',
          500: '#667085',
          400: '#8A93A5',
          300: '#B4BAC6',
        },
        line: {
          DEFAULT: '#E4E7EC',
          soft: '#EEF1F5',
          strong: '#D0D5DD',
        },
        /* Status — used only for compliance and risk semantics */
        success: {
          50: '#ECFDF3',
          100: '#D1FADF',
          600: '#16A34A',
          700: '#15803D',
        },
        warning: {
          50: '#FFFAEB',
          100: '#FEF0C7',
          600: '#D97706',
          700: '#B45309',
        },
        danger: {
          50: '#FEF3F2',
          100: '#FEE4E2',
          600: '#DC2626',
          700: '#B91C1C',
        },
        critical: {
          50: '#FEF3F2',
          600: '#B42318',
          700: '#912018',
        },
        neutralbadge: {
          50: '#F2F4F7',
          600: '#475467',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      fontSize: {
        micro: ['11px', { lineHeight: '16px', letterSpacing: '0.06em' }],
        label: ['12px', { lineHeight: '16px', letterSpacing: '0.05em' }],
        xs: ['12px', { lineHeight: '18px' }],
        13: ['13px', { lineHeight: '20px' }],
        sm: ['14px', { lineHeight: '20px' }],
        base: ['15px', { lineHeight: '24px' }],
        lg: ['16px', { lineHeight: '24px' }],
        'section': ['18px', { lineHeight: '26px', letterSpacing: '-0.01em' }],
        'kpi': ['28px', { lineHeight: '34px', letterSpacing: '-0.02em' }],
        'page': ['30px', { lineHeight: '38px', letterSpacing: '-0.02em' }],
        'display': ['38px', { lineHeight: '46px', letterSpacing: '-0.025em' }],
      },
      borderRadius: {
        sm: '4px',
        DEFAULT: '6px',
        md: '6px',
        lg: '8px',
        xl: '10px',
        '2xl': '12px',
      },
      boxShadow: {
        card: '0 1px 2px rgba(16, 24, 40, 0.04)',
        raised: '0 1px 3px rgba(16, 24, 40, 0.06), 0 1px 2px rgba(16, 24, 40, 0.04)',
        pop: '0 8px 24px -4px rgba(11, 31, 51, 0.10), 0 2px 6px -2px rgba(11, 31, 51, 0.05)',
        modal: '0 24px 48px -12px rgba(11, 31, 51, 0.24)',
        inset: 'inset 0 1px 0 rgba(255,255,255,0.04)',
      },
      spacing: {
        sidebar: '260px',
        header: '60px',
      },
      keyframes: {
        'fade-in': {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(6px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'scan-sweep': {
          '0%': { transform: 'translateY(-8%)', opacity: '0.15' },
          '50%': { opacity: '0.5' },
          '100%': { transform: 'translateY(108%)', opacity: '0.15' },
        },
        'bar-grow': {
          '0%': { width: '0%' },
        },
        'pulse-soft': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.45' },
        },
      },
      animation: {
        'fade-in': 'fade-in 240ms ease-out both',
        'fade-up': 'fade-up 280ms cubic-bezier(0.16, 1, 0.3, 1) both',
        'scan-sweep': 'scan-sweep 2.1s ease-in-out infinite',
        'bar-grow': 'bar-grow 700ms cubic-bezier(0.16, 1, 0.3, 1) both',
        'pulse-soft': 'pulse-soft 1.8s ease-in-out infinite',
      },
      transitionTimingFunction: {
        'out-expo': 'cubic-bezier(0.16, 1, 0.3, 1)',
      },
    },
  },
  plugins: [],
};
