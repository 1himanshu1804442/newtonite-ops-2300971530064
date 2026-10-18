import React, { Component, ErrorInfo, ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
      errorInfo: null,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    // Log explicit context for debugging
    console.error('[ErrorBoundary] Caught unhandled rendering exception:', error, errorInfo);
    this.setState({
      error,
      errorInfo,
    });
  }

  handleReload = (): void => {
    window.location.reload();
  };

  render(): ReactNode {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen bg-[#0B0F17] flex items-center justify-center p-6 text-gray-200">
          <div className="max-w-xl w-full bg-[#111827] border border-red-900/60 rounded-xl p-8 shadow-2xl">
            <div className="flex items-center space-x-3 mb-4">
              <div className="p-3 bg-red-950/60 rounded-lg text-red-400">
                <AlertTriangle className="w-8 h-8" />
              </div>
              <div>
                <h2 className="text-xl font-bold text-white">Application Exception Detected</h2>
                <p className="text-sm text-gray-400">
                  The UI encountered an unexpected error while rendering this view.
                </p>
              </div>
            </div>

            <div className="bg-[#0B0F17] p-4 rounded-lg border border-gray-800 font-mono text-xs text-red-300 overflow-x-auto my-4 max-h-48">
              <div className="font-semibold mb-1">{this.state.error?.name}: {this.state.error?.message}</div>
              <div className="text-gray-500 whitespace-pre-wrap">{this.state.errorInfo?.componentStack}</div>
            </div>

            <div className="flex items-center justify-end space-x-3 pt-4 border-t border-gray-800">
              <button
                onClick={this.handleReload}
                className="inline-flex items-center space-x-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-lg transition-colors shadow-md"
              >
                <RefreshCw className="w-4 h-4" />
                <span>Reload Application</span>
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
