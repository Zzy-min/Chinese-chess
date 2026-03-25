import { ProfilePage } from '../../components/profile/profile-page';
import { getApiBase } from '../../lib/api-base';

export default function Page() {
  return <ProfilePage apiBase={getApiBase()} />;
}
