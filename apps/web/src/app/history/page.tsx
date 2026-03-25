import { HistoryPage } from '../../components/history/history-page';
import { getApiBase } from '../../lib/api-base';

export default function Page() {
  return <HistoryPage apiBase={getApiBase()} />;
}
