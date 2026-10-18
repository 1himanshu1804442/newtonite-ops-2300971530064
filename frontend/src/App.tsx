import React from 'react';
import { ErrorBoundary } from './components/ErrorBoundary';
import { DashboardPage } from './pages/DashboardPage';

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <DashboardPage />
    </ErrorBoundary>
  );
};

export default App;
