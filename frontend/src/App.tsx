import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ThemeProvider, useTheme } from './theme';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { PostsPage } from './pages/PostsPage';
import { PostDetailPage } from './pages/PostDetailPage';
import { CreatePostPage } from './pages/CreatePostPage';
import { ProfilePage } from './pages/ProfilePage';
import { ChatListPage } from './pages/ChatListPage';
import { ChatPage } from './pages/ChatPage';
import { UsersPage } from './pages/UsersPage';
import { MyOrdersPage } from './pages/MyOrdersPage';
import { FavoritesPage } from './pages/FavoritesPage';
import { HelpGraphPage } from './pages/HelpGraphPage';
import { AchievementsPage } from './pages/AchievementsPage';
import { ActivityPage } from './pages/ActivityPage';
import { MapPage } from './pages/MapPage';
import { Navbar } from './components/Navbar';
import { Layout } from './components/Layout';
import { ToastProvider } from './components/Toast';

const ProtectedRoute = ({ children }: { children: React.ReactNode }) => {
  const { user, isLoading } = useAuth();
  
  if (isLoading) return <div style={styles.loading}>Загрузка...</div>;
  if (!user) return <Navigate to="/login" />;
  
  return <>{children}</>;
};

const AppLayout = () => {
  return (
    <Layout>
      <Navbar />
      <main style={styles.main}>
        <Routes>
          <Route path="/login" element={<Navigate to="/" />} />
          <Route path="/register" element={<Navigate to="/" />} />
          <Route path="/" element={<PostsPage />} />
          <Route path="/posts/new" element={
            <ProtectedRoute>
              <CreatePostPage />
            </ProtectedRoute>
          } />
          <Route path="/posts/:id" element={<PostDetailPage />} />
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
              <MapPage />
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

const ThemedApp = () => {
  const { theme } = useTheme();
  const isDark = theme.mode === 'dark';
  
  return (
    <>
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
          background: ${isDark ? '#022c22' : '#f0fdfa'};
          color: ${isDark ? '#ffffff' : '#134e4a'};
          transition: background 0.3s ease, color 0.3s ease;
        }
        select {
          background-color: ${isDark ? '#065F46' : '#ffffff'} !important;
          color: ${isDark ? '#ffffff' : '#134e4a'} !important;
          border: 1px solid ${isDark ? 'rgba(255, 255, 255, 0.2)' : 'rgba(0, 0, 0, 0.1)'} !important;
        }
        select option {
          background-color: ${isDark ? '#065F46' : '#ffffff'} !important;
          color: ${isDark ? '#ffffff' : '#134e4a'} !important;
        }
        select:hover {
          border-color: ${isDark ? '#22d3ee' : '#06b6d4'} !important;
          box-shadow: 0 0 10px ${isDark ? 'rgba(34, 211, 238, 0.3)' : 'rgba(6, 182, 212, 0.3)'};
        }
        input:hover, input:focus, textarea:hover, textarea:focus {
          border-color: ${isDark ? '#22d3ee' : '#06b6d4'} !important;
          box-shadow: 0 0 15px ${isDark ? 'rgba(34, 211, 238, 0.3)' : 'rgba(6, 182, 212, 0.3)'} !important;
        }
        .post-card:hover {
          transform: translateY(-8px) scale(1.02);
        }
      `}</style>
      <AppContent />
    </>
  );
};

function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <ThemeProvider>
          <BrowserRouter>
            <ThemedApp />
          </BrowserRouter>
        </ThemeProvider>
      </ToastProvider>
    </AuthProvider>
  );
}

const styles: Record<string, React.CSSProperties> = {
  main: {
    padding: '24px',
    maxWidth: '1200px',
    margin: '0 auto',
  },
  loading: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100vh',
    color: '#fff',
    fontSize: '18px',
  },
};

export default App;
