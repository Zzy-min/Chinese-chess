import { ReviewPage } from '../../../components/review/review-page';
import { getApiBase } from '../../../lib/api-base';

export default async function Page({ params }: { params: Promise<{ archiveId: string }> }) {
  const { archiveId } = await params;
  return <ReviewPage apiBase={getApiBase()} archiveId={archiveId} />;
}
