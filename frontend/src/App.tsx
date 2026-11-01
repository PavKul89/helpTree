import { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { PostsPage } from './pages/PostsPage';
import { HomePage } from './pages/HomePage';
import { PostDetailPage } from './pages/PostDetailPage';
import { CreatePostPage } from './pages/CreatePostPage';
import { EditPostPage } from './pages/EditPostPage';
import { ProfilePage } from './pages/ProfilePage';
import { ChatListPage } from './pages/ChatListPage';
import { ChatPage } from './pages/ChatPage';
import { UsersPage } from './pages/UsersPage';
import { MyOrdersPage } from './pages/MyOrdersPage';
import { FavoritesPage } from './pages/FavoritesPage';
import { HelpGraphPage } from './pages/HelpGraphPage';
import { AchievementsPage } from './pages/AchievementsPage';
import { ActivityPage } from './pages/ActivityPage';
import { WalletPage } from './pages/WalletPage';
import { Navbar } from './components/Navbar';
import { Layout } from './components/Layout';
import { ToastProvider } from './components/Toast';

// Leaflet and the map page are loaded only when /map is opened.
const MapPage = lazy(() => import('./pages/MapPage'));

const MapPageFallback = () => (
  <div style={styles.mapFallback}>
    <div style={styles.mapFallbackCard}>
      <div style={styles.mapFallbackSpinner} />
      <p style={styles.mapFallbackText}>Загружаем карту…</p>
    </div>
  </div>
);

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { user, isLoading } = useAuth();
  
  if (isLoading) return <div style={styles.loading}>Загрузка...</div>;
  if (!user) return <Navigate to="/login" />;
  
  return <>{children}</>;
};

const AppLayout = () => {
  return (
    <>
      <Navbar />
      <Layout>
        <main style={styles.main}>
          <Routes>
            <Route path="/login" element={<Navigate to="/" />} />
            <Route path="/register" element={<Navigate to="/" />} />
            <Route path="/" element={<HomePage />} />
            <Route path="/posts" element={<PostsPage />} />
            <Route path="/posts/new" element={
              <ProtectedRoute>
                <CreatePostPage />
              </ProtectedRoute>
            } />
            <Route path="/posts/:id" element={<PostDetailPage />} />
            <Route path="/posts/:id/edit" element={
              <ProtectedRoute>
                <EditPostPage />
              </ProtectedRoute>
            } />
            <Route path="/users" element={<UsersPage />} />
            <Route path="/my-orders" element={
              <ProtectedRoute>
                <MyOrdersPage />
              </ProtectedRoute>
            } />
            <Route path="/favorites" element={
              <ProtectedRoute>
                <FavoritesPage />
              </ProtectedRoute>
            } />
            <Route path="/graph" element={<HelpGraphPage />} />
            <Route path="/map" element={
              <ProtectedRoute>
                <Suspense fallback={<MapPageFallback />}>
                  <MapPage />
                </Suspense>
              </ProtectedRoute>
            } />
            <Route path="/achievements" element={<AchievementsPage />} />
            <Route path="/activity" element={
              <ProtectedRoute>
                <ActivityPage />
              </ProtectedRoute>
            } />
            <Route path="/activity/:userId" element={<ActivityPage />} />
            <Route path="/profile" element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            } />
            <Route path="/profile/:userId" element={<ProfilePage key={window.location.pathname} />} />
            <Route 
              path="/wallet" 
              element={
                <ProtectedRoute>
                  <WalletPage />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="/chats" 
              element={
                <ProtectedRoute>
                  <ChatListPage />
                </ProtectedRoute>
              } 
            />
            <Route 
              path="/chats/:id" 
              element={
                <ProtectedRoute>
                  <ChatPage />
                </ProtectedRoute>
              } 
            />
          </Routes>
        </main>
      </Layout>
    </>
  );
};

function AppContent() {
  const location = useLocation();
  const isAuthPage = location.pathname === '/login' || location.pathname === '/register';
  
  return isAuthPage ? (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
    </Routes>
  ) : (
    <AppLayout />
  );
}

function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
        <style>{`
          @keyframes spin {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
          @keyframes sway {
            from { transform: rotate(-2deg); }
            to { transform: rotate(2deg); }
          }
          @keyframes swayLeft {
            from { transform: rotate(2deg); }
            to { transform: rotate(-2deg); }
          }
          @keyframes slideUp {
            from { opacity: 0; transform: translateY(20px); }
            to { opacity: 1; transform: translateY(0); }
          }
          @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
          }
          * {
            box-sizing: border-box;
          }
          body {
            margin: 0;
            padding: 0;
          }
          select {
            background-color: #102d20 !important;
            color: #f5f8f1 !important;
            border: 1px solid rgba(180, 225, 155, 0.2) !important;
          }
          select option {
            background-color: #102d20 !important;
            color: #f5f8f1 !important;
          }
          select:hover {
            border-color: rgba(200, 245, 106, 0.6) !important;
            box-shadow: 0 0 10px rgba(200, 245, 106, 0.18);
          }
          .post-card:hover {
            box-shadow: 0 12px 24px -4px rgba(0, 0, 0, 0.3);
          }
        `}</style>
        <AppContent />
      </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  );
}

const styles: Record<string, React.CSSProperties> = {
  main: {
    padding: 0,
  },
  loading: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100vh',
    color: '#fff',
    fontSize: '18px',
  },
  mapFallback: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 'calc(100vh - 72px)',
    padding: '24px',
  },
  mapFallbackCard: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '14px',
    padding: '30px 36px',
    borderRadius: '20px',
    border: '1px solid rgba(180, 225, 155, 0.2)',
    background: '#102D20',
    boxShadow: '0 20px 44px rgba(0, 0, 0, 0.28)',
  },
  mapFallbackSpinner: {
    width: '34px',
    height: '34px',
    borderRadius: '50%',
    border: '3px solid rgba(200, 245, 106, 0.18)',
    borderTopColor: '#C8F56A',
    animation: 'spin 1s linear infinite',
  },
  mapFallbackText: {
    margin: 0,
    color: '#B7C5B8',
    fontSize: '14px',
  },
};

export default App;
