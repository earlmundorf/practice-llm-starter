import { useContext } from 'react';
import { DarkModeContext } from '../contexts/darkMode.context';
import type { DarkModeContextType } from '../types';

export const useDarkMode = (): DarkModeContextType => {
  const context = useContext(DarkModeContext);
  if (!context) {
    throw new Error('useDarkMode must be used within a DarkModeProvider');
  }
  return context;
};
