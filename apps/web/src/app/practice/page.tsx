import { PracticePage } from '../../components/practice/practice-page';
import { getApiBase } from '../../lib/api-base';

export default function Page() {
  return <PracticePage apiBase={getApiBase()} />;
}
