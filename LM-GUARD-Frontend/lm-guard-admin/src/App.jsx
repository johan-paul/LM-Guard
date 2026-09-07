import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate, useParams } from 'react-router-dom';

import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppLayout from './layouts/AppLayout';

import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Inspections from './pages/Inspections';
import InspectionDetail from './pages/InspectionDetail';
import Violations from './pages/Violations';
import ViolationDetail from './pages/ViolationDetail';
import InspectorManagement from './pages/InspectorManagement';
import RiskIntelligence from './pages/RiskIntelligence';
import Products from './pages/Products';
import ProductDetail from './pages/ProductDetail';
import ProductHistory from './pages/ProductHistory';
import Rules from './pages/Rules';
import Analytics from './pages/Analytics';
import Settings from './pages/Settings';
import NewInspection from './pages/NewInspection';
import Evidence from './pages/Evidence';
import NotFound from './pages/NotFound';

/** Blank frame while a stored token is re-validated against the backend. */
function SessionGate({ children }) {
  const { checkingSession } = useAuth();
  if (checkingSession) return null;
  return children;
}

/** Keeps signed-in officers away from the auth screens. */
function PublicOnly({ children }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <Navigate to="/dashboard" replace /> : children;
}

/** Authenticated page inside the application shell. */
function Private({ children }) {
  return (
    <SessionGate>
      <ProtectedRoute>
        <AppLayout>{children}</AppLayout>
      </ProtectedRoute>
    </SessionGate>
  );
}

/** Legacy path support: /inspection/:id → /inspections/:id */
function LegacyInspectionRedirect() {
  const { id } = useParams();
  return <Navigate to={`/inspections/${id}`} replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <Router>
        <Routes>
          {/* Authentication */}
          <Route
            path="/login"
            element={
              <PublicOnly>
                <Login />
              </PublicOnly>
            }
          />
          <Route
            path="/register"
            element={
              <PublicOnly>
                <Register />
              </PublicOnly>
            }
          />

          {/* Operations */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<Private><Dashboard /></Private>} />
          <Route path="/inspections" element={<Private><Inspections /></Private>} />
          <Route path="/inspections/:id" element={<Private><InspectionDetail /></Private>} />
          <Route path="/violations" element={<Private><Violations /></Private>} />
          <Route path="/violations/:id" element={<Private><ViolationDetail /></Private>} />
          <Route path="/inspectors" element={<Private><InspectorManagement /></Private>} />

          {/* Intelligence */}
          <Route path="/risk" element={<Private><RiskIntelligence /></Private>} />
          <Route path="/products" element={<Private><Products /></Private>} />
          <Route path="/products/:id" element={<Private><ProductDetail /></Private>} />
          <Route path="/product-history" element={<Private><ProductHistory /></Private>} />

          {/* Governance */}
          <Route path="/rules" element={<Private><Rules /></Private>} />
          <Route path="/analytics" element={<Private><Analytics /></Private>} />
          <Route path="/settings" element={<Private><Settings /></Private>} />

          {/* Inspection workflow */}
          <Route path="/new-inspection" element={<Private><NewInspection /></Private>} />
          <Route path="/evidence/:id" element={<Private><Evidence /></Private>} />

          {/* Legacy paths from the previous console */}
          <Route path="/history" element={<Navigate to="/inspections" replace />} />
          <Route path="/inspection/:id" element={<LegacyInspectionRedirect />} />

          <Route path="*" element={<Private><NotFound /></Private>} />
        </Routes>
      </Router>
    </AuthProvider>
  );
}
