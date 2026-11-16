import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/Login/LoginPage';
import DashboardPage from './pages/Dashboard/DashboardPage';
import RegisterPage from './pages/Register/RegisterPage';
import VerifyEmailPage from './pages/VerifyEmail/VerifyEmailPage';
import ForgotPwdPage from './pages/ForgotPassword/ForgotPwdPage';
import HomePage from './pages/Home/HomePage';
import UserPage from './pages/User/UserPage';
import CreateDevicePage from './pages/CreateDevice/CreateDevicePage';
import DevicePage from './pages/Device/DevicePage';
import CreateCredentialPage from './pages/CreateCredential/CreateCredentialPage';
import CredentialPage from './pages/Credential/CredentialPage';
import CreateOidConfigPage from './pages/CreateOidConfig/CreateOidConfigPage';
import OidConfigPage from './pages/OidConfig/OidConfigPage';
import InterfacePage from './pages/Interface/InterfacePage';

const ProtectedRoute = ({ children }) => {
  const token = localStorage.getItem('access_token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return children;
};

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/forgot-password" element={<ForgotPwdPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />

        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <DashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/register"
          element={
            <ProtectedRoute>
              <RegisterPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/home"
          element={
            <ProtectedRoute>
              <HomePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/users/:id"
          element={
            <ProtectedRoute>
              <UserPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/devices/create"
          element={<CreateDevicePage />}
        />
        <Route
          path="/devices/:deviceId"
          element={<DevicePage />}
        />
        <Route
          path="/devices/:deviceId/credentials/create"
          element={<CreateCredentialPage />}
        />
        <Route
          path="/credentials/:id"
          element={<CredentialPage />}
        />
        <Route
          path="/devices/:deviceId/oid-configs/create"
          element={<CreateOidConfigPage />}
        />
        <Route
          path="/oid-configs/:id"
          element={<OidConfigPage />}
        />
        <Route
          path="/devices/:deviceId/interfaces/:interfaceIndex"
          element={<InterfacePage />}
        />

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Router>
  );
}

export default App;