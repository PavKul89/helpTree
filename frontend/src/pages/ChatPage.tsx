import { useLayoutEffect } from 'react';
import { ChatWorkspace } from '../components/ChatWorkspace';

export const ChatPage = () => {
  useLayoutEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  return <ChatWorkspace />;
};

export default ChatPage;
