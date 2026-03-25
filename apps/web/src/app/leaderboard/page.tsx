import { LeaderboardPage } from '../../components/leaderboard/leaderboard-page';
import { getApiBase } from '../../lib/api-base';

export default function Page() {
  return <LeaderboardPage apiBase={getApiBase()} />;
}
