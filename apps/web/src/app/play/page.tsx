import { PlayPage } from '../../components/play/play-page';
import { getApiBase } from '../../lib/api-base';

export default function Page() {
  return <PlayPage apiBase={getApiBase()} />;
}
