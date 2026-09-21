import { defineNode, z } from '@midscene/test';
import { defineTestProject } from '@midscene/test/config';

const echo = defineNode({
  name: 'runtime.echo',
  description: 'Return a message to verify the embedded Test Runner.',
  inputSchema: z.object({ message: z.string() }),
  execute({ input }) {
    return { echoed: input.message };
  },
});

export default defineTestProject({
  nodes: [echo],
  projects: [
    {
      name: 'android-local',
      files: { include: ['basic.yaml'] },
    },
  ],
});
