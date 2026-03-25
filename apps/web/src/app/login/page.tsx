import { AuthForm } from '../../components/auth/auth-form';
import { getApiBase } from '../../lib/api-base';

export default function Page() {
  return <AuthForm apiBase={getApiBase()} mode="login" />;
}
