import { GameCatalog } from './game-catalog';
import { Hero } from './hero';
import { CapabilityPanel } from '../status/capability-panel';

export function HomePage({ showCapabilities = true }: { showCapabilities?: boolean }) {
  return (
    <div className="pageShell">
      <Hero />
      {showCapabilities ? <CapabilityPanel /> : null}
      <GameCatalog />
    </div>
  );
}
