import { RoomDetailLoader } from '../../../components/play/room-detail-loader';
import { getApiBase } from '../../../lib/api-base';

export default async function Page({ params }: { params: Promise<{ roomId: string }> }) {
  const { roomId } = await params;
  return <RoomDetailLoader apiBase={getApiBase()} roomId={roomId} />;
}
