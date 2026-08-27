import type { GlobalThemeOverrides } from 'naive-ui';

export const learnflowThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#176b68',
    primaryColorHover: '#21817d',
    primaryColorPressed: '#115652',
    primaryColorSuppl: '#21817d',
    infoColor: '#2563eb',
    successColor: '#16835b',
    warningColor: '#c87622',
    errorColor: '#c33b46',
    borderRadius: '12px',
    borderRadiusSmall: '9px',
    fontFamily: "'Inter', 'Segoe UI Variable Text', 'PingFang SC', 'Microsoft YaHei', sans-serif"
  },
  Button: {
    borderRadiusMedium: '12px',
    borderRadiusLarge: '14px',
    fontWeight: '650'
  },
  Card: {
    borderRadius: '18px'
  },
  Input: {
    borderRadius: '12px'
  }
};
