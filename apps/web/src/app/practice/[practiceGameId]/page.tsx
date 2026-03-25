import { PracticeDetailLoader } from '../../../components/practice/practice-detail-loader';
import { getApiBase } from '../../../lib/api-base';

export default async function Page({ params }: { params: Promise<{ practiceGameId: string }> }) {
  const { practiceGameId } = await params;
  return <PracticeDetailLoader apiBase={getApiBase()} practiceGameId={practiceGameId} />;
}
