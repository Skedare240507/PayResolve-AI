import { Link } from 'react-router-dom';
import { PayResolveMark } from '../components/PayResolveLogo';
import { ThemeToggle } from '../components/ThemeToggle';
import { GoogleIcon } from '../components/GoogleIcon';

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-white dark:bg-[#0a111a] text-slate-900 dark:text-slate-100 font-sans selection:bg-blue-100 selection:text-blue-900">
      
      {/* ────────────────── TOP HERO SECTION & INTEGRATED NAVBAR ────────────────── */}
      <section className="bg-gradient-to-b from-[#072152] via-[#06357b] to-[#044093] text-white overflow-hidden relative">
        
        {/* Seamless Navigation Bar */}
        <nav className="max-w-[1240px] mx-auto px-6 sm:px-8 lg:px-12 pt-6 pb-4 flex items-center justify-between relative z-20">
          <div className="flex items-center space-x-8 lg:space-x-12">
            {/* Logo Badge in Frosted Pill */}
            <Link
              to="/"
              className="inline-flex items-center space-x-2.5 px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/15 border border-white/20 text-white shadow-sm cursor-pointer backdrop-blur-xs transition-colors"
            >
              <PayResolveMark size={22} />
              <span className="text-sm font-semibold tracking-tight text-white flex items-center gap-1.5">
                PayResolve
                <span className="text-[10px] bg-blue-500/80 px-1 py-0.5 rounded text-white font-mono leading-none">
                  AI
                </span>
              </span>
            </Link>

            {/* Nav links */}
            <div className="hidden md:flex items-center space-x-7 text-xs font-normal text-white/90">
              <a href="#risk" className="hover:text-white transition-colors">Market risk</a>
              <a href="#how-it-works" className="hover:text-white transition-colors">How it works</a>
              <a href="#product" className="hover:text-white transition-colors">The product</a>
              <a href="#sources" className="hover:text-white transition-colors">Sources</a>
            </div>
          </div>

          <div className="flex items-center space-x-3">
            <ThemeToggle variant="nav" />
            <Link
              to="/signin"
              className="text-xs font-medium text-white border border-white/30 hover:bg-white/10 active:scale-[0.97] px-4 py-2 rounded-lg transition-all cursor-pointer"
            >
              Sign in
            </Link>
            <Link
              to="/queue"
              className="text-xs font-semibold bg-white text-slate-900 hover:bg-slate-100 active:scale-[0.97] px-4 py-2 rounded-lg shadow-sm transition-all cursor-pointer inline-block"
            >
              Open workspace
            </Link>
          </div>
        </nav>

        {/* Hero Content Area */}
        <div className="max-w-[1240px] mx-auto px-6 sm:px-8 lg:px-12 pt-10 pb-24 lg:pt-16 lg:pb-32 relative z-10">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-14 items-center">
            
            {/* Left Column: Hero Text */}
            <div className="lg:col-span-6 space-y-6">
              <p className="text-xs text-blue-200/90 font-normal">
                PayPal AI Hackathon 2026. Sandbox build.
              </p>

              <h1 className="text-5xl sm:text-6xl lg:text-[76px] font-black tracking-tight leading-[1.04] text-white">
                Customer<br />
                says paid.<br />
                System says<br />
                unpaid.
              </h1>

              <p className="text-sm sm:text-base text-blue-100/90 leading-relaxed font-normal max-w-lg">
                PayResolve puts every PayPal event, order, refund and shipment on one evidence map, then lets an AI investigator explain what is confirmed and what is missing. You approve every fix.
              </p>

              <div className="flex flex-wrap items-center gap-3 pt-2">
                <Link
                  to="/queue"
                  className="bg-white text-slate-900 px-5 py-2.5 rounded-lg font-semibold text-xs sm:text-sm shadow-md hover:bg-slate-100 active:scale-[0.97] transition-all cursor-pointer"
                >
                  Open workspace
                </Link>
                <a
                  href="#how-it-works"
                  className="border border-white/40 bg-transparent text-white hover:bg-white/10 active:scale-[0.97] px-5 py-2.5 rounded-lg font-medium text-xs sm:text-sm transition-all cursor-pointer"
                >
                  See how it works
                </a>
              </div>
            </div>

            {/* Right Column: Hero Graphic - Case PR-0142 Workspace Mockup */}
            <div className="lg:col-span-6 relative">
              
              {/* Window Card */}
              <div className="bg-[#0b121f] rounded-2xl border border-slate-700/60 p-5 sm:p-6 shadow-2xl relative space-y-4">
                
                {/* Window header dots + title */}
                <div className="flex items-center justify-between pb-2 border-b border-slate-800">
                  <div className="flex items-center space-x-2 text-[11px] text-slate-400 font-mono">
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-600 inline-block" />
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-600 inline-block" />
                    <span className="w-2.5 h-2.5 rounded-full bg-slate-600 inline-block" />
                    <span className="ml-2 font-medium text-slate-300">Case PR-0142 · Evidence graph</span>
                  </div>
                </div>

                {/* Graph mockup canvas */}
                <div className="relative w-full h-72 bg-[#070d18] rounded-xl border border-slate-800 p-4 overflow-hidden">
                  
                  {/* Connection lines SVG */}
                  <svg className="absolute inset-0 w-full h-full pointer-events-none" xmlns="http://www.w3.org/2000/svg">
                    {/* Line from PayPal order to Capture */}
                    <path d="M 120 135 L 210 55" stroke="#334155" strokeWidth="1.5" fill="none" />
                    {/* Line from PayPal order to Webhook */}
                    <path d="M 120 135 L 210 220" stroke="#334155" strokeWidth="1.5" fill="none" />
                    {/* Missing status update edge from Capture to Shop Order (dashed yellow) */}
                    <path d="M 330 55 L 430 135" stroke="#f59e0b" strokeWidth="1.5" strokeDasharray="4 4" fill="none" />
                    {/* Line from Webhook to Shop order */}
                    <path d="M 330 220 L 430 135" stroke="#334155" strokeWidth="1.5" fill="none" />
                  </svg>

                  {/* Top node: Capture */}
                  <div className="absolute left-[36%] top-3 bg-[#121a2a] border border-cyan-400/80 rounded-lg p-2 text-left shadow-lg">
                    <div className="text-[10px] font-mono text-cyan-300 font-bold uppercase tracking-wide">Capture</div>
                    <div className="text-[11px] font-mono text-white font-medium">COMPLETED, $84.50</div>
                  </div>

                  {/* Left node: PayPal order */}
                  <div className="absolute left-3 top-[38%] bg-[#121a2a] border border-slate-700 rounded-lg p-2 text-left shadow-md">
                    <div className="text-[10px] font-mono text-slate-400 font-semibold">PayPal order</div>
                    <div className="text-[11px] font-mono text-slate-200">approved</div>
                  </div>

                  {/* Annotation pill on the dashed missing line */}
                  <div className="absolute right-[22%] top-[20%] z-10 bg-amber-950/90 border border-amber-500/80 text-amber-300 text-[10px] font-mono px-2 py-0.5 rounded shadow">
                    missing: PAID update
                  </div>

                  {/* Right node: Shop order (Yellow border) */}
                  <div className="absolute right-3 top-[38%] bg-[#121a2a] border-2 border-amber-500/90 rounded-lg p-2 text-left shadow-lg">
                    <div className="text-[10px] font-mono text-amber-400 font-bold">Shop order #10482</div>
                    <div className="text-[11px] font-mono text-amber-200">still PENDING</div>
                  </div>

                  {/* Bottom node: Webhook */}
                  <div className="absolute left-[36%] bottom-3 bg-[#121a2a] border border-slate-700 rounded-lg p-2 text-left shadow-md">
                    <div className="text-[10px] font-mono text-slate-400 font-semibold">Webhook</div>
                    <div className="text-[11px] font-mono text-slate-200">delivered twice</div>
                  </div>

                  {/* Dashed placeholder for shipment */}
                  <div className="absolute right-3 bottom-3 border border-dashed border-slate-700 rounded-lg px-2.5 py-1 text-left text-slate-500 text-[10px] bg-slate-900/30">
                    No shipment yet
                  </div>
                </div>

                {/* Bottom pill buttons */}
                <div className="flex items-center space-x-2 pt-1 font-mono text-[10px]">
                  <span className="px-3 py-1 rounded-full border border-blue-500/40 bg-blue-950/40 text-blue-300">
                    getCaptureDetails 212 ms
                  </span>
                  <span className="px-3 py-1 rounded-full border border-blue-500/40 bg-blue-950/40 text-blue-300">
                    getShopOrder 38 ms
                  </span>
                </div>

                {/* Floating AI investigator card overlapping bottom-right */}
                <div className="absolute -bottom-8 -right-3 sm:-right-6 w-[310px] bg-white text-slate-900 rounded-2xl p-4 shadow-2xl border border-slate-100/90 text-xs space-y-2 z-20">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-1.5 font-bold text-slate-900 text-sm">
                      <GoogleIcon name="psychology" size={18} className="text-blue-600" />
                      <span>AI investigator</span>
                    </div>
                    <span className="text-[10px] px-2 py-0.5 rounded-full border border-emerald-500/60 text-emerald-700 bg-emerald-50 font-medium">
                      3 of 3 verified
                    </span>
                  </div>

                  <div className="space-y-1.5 text-xs text-slate-700">
                    <div className="flex items-start gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 mt-1.5 shrink-0" />
                      <div>
                        <strong className="text-slate-900">Confirmed:</strong> capture COMPLETED, $84.50.
                      </div>
                    </div>
                    <div className="flex items-start gap-1.5">
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-500 mt-1.5 shrink-0" />
                      <div>
                        <strong className="text-slate-900">Missing:</strong> status update after the webhook.
                      </div>
                    </div>
                    <div className="flex items-start gap-1.5 text-slate-500">
                      <span className="w-1.5 h-1.5 rounded-full bg-blue-500 mt-1.5 shrink-0" />
                      <div>
                        <strong className="text-slate-700">Proposed:</strong> sync order status. Awaiting approval.
                      </div>
                    </div>
                  </div>
                </div>

              </div>
            </div>

          </div>
        </div>
      </section>

      {/* ────────────────── SECTION: BUILT WITH PARTNERS ────────────────── */}
      <section className="py-12 border-y border-slate-200/80 dark:border-slate-800/80 bg-slate-50/60 dark:bg-[#0c121d]">
        <div className="max-w-[1240px] mx-auto px-6 sm:px-8 lg:px-12 space-y-6">
          <div className="text-center space-y-1">
            <span className="text-xs font-semibold tracking-wider uppercase text-blue-600 dark:text-blue-400">
              PayPal AI Hackathon Partner Ecosystem
            </span>
            <h2 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white">
              Built with industry-leading developer platforms
            </h2>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <div className="p-4 rounded-xl bg-white dark:bg-[#111827] border border-slate-200/80 dark:border-slate-800/80 shadow-2xs space-y-1.5 text-center">
              <div className="font-bold text-sm text-[#003087] dark:text-[#0079C1]">PayPal</div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
                Orders v2, server captures &amp; webhook verification
              </p>
            </div>

            <div className="p-4 rounded-xl bg-white dark:bg-[#111827] border border-slate-200/80 dark:border-slate-800/80 shadow-2xs space-y-1.5 text-center">
              <div className="font-bold text-sm text-red-600 dark:text-red-400">AG Grid &amp; Studio</div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
                Operations dashboard, custom widgets &amp; queue
              </p>
            </div>

            <div className="p-4 rounded-xl bg-white dark:bg-[#111827] border border-slate-200/80 dark:border-slate-800/80 shadow-2xs space-y-1.5 text-center">
              <div className="font-bold text-sm text-orange-500">Groq</div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
                Llama 3.3 70B ultra-fast primary text router
              </p>
            </div>

            <div className="p-4 rounded-xl bg-white dark:bg-[#111827] border border-slate-200/80 dark:border-slate-800/80 shadow-2xs space-y-1.5 text-center">
              <div className="font-bold text-sm text-blue-500">Google Gemini</div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
                Gemini 2.0 Flash multimodal evidence extraction
              </p>
            </div>

            <div className="p-4 rounded-xl bg-white dark:bg-[#111827] border border-slate-200/80 dark:border-slate-800/80 shadow-2xs space-y-1.5 text-center">
              <div className="font-bold text-sm text-emerald-600 dark:text-emerald-400">Render</div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
                Blueprint deploy, Dockerfile &amp; health checks
              </p>
            </div>

            <div className="p-4 rounded-xl bg-white dark:bg-[#111827] border border-slate-200/80 dark:border-slate-800/80 shadow-2xs space-y-1.5 text-center">
              <div className="font-bold text-sm text-[#FF6C37]">Postman</div>
              <p className="text-[11px] text-slate-500 dark:text-slate-400 leading-tight">
                End-to-end API test collections &amp; automation
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── SECTION: MARKET RISK ────────────────── */}
      <section id="risk" className="py-20 lg:py-28 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto space-y-16">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-16 items-start">
          
          {/* Left Column: Title and Subtitle */}
          <div className="lg:col-span-5 space-y-4">
            <h2 className="text-4xl sm:text-5xl font-black tracking-tight text-slate-900 dark:text-white leading-[1.08]">
              Disputes are<br />
              growing. The<br />
              evidence is<br />
              scattered.
            </h2>
            <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed pt-1">
              Recent industry research points one way: more disputes, thin teams, and AI-initiated purchases with no settled rules. These are vendor and trade-body surveys, quoted as published.
            </p>
          </div>

          {/* Right Column: 4 Stats & 4 Literature Citations */}
          <div className="lg:col-span-7 space-y-8">
            
            {/* 4 Stats Rows */}
            <div className="space-y-6">
              
              {/* Stat 1 */}
              <div className="pb-6 border-b border-slate-100 dark:border-slate-800/80 flex flex-col sm:flex-row sm:items-baseline justify-between gap-4">
                <div className="text-5xl sm:text-6xl font-black text-[#0052cc] dark:text-[#3b82f6] tracking-tight shrink-0 w-56 sm:w-64">
                  83.4%
                </div>
                <div className="flex-1 text-xs sm:text-sm text-slate-700 dark:text-slate-300 pr-4">
                  of enterprise merchants report friendly fraud rising over three years.
                </div>
                <a
                  href="https://chargebacks911.com/2026-chargeback-field-report/"
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium"
                >
                  <span>Chargebacks911</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              {/* Stat 2 */}
              <div className="pb-6 border-b border-slate-100 dark:border-slate-800/80 flex flex-col sm:flex-row sm:items-baseline justify-between gap-4">
                <div className="text-5xl sm:text-6xl font-black text-[#0052cc] dark:text-[#3b82f6] tracking-tight shrink-0 w-56 sm:w-64">
                  38%
                </div>
                <div className="flex-1 text-xs sm:text-sm text-slate-700 dark:text-slate-300 pr-4">
                  of merchants say chargeback costs now influence what they charge.
                </div>
                <a
                  href="https://greensheet.com/newswire&article_id=64300"
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium"
                >
                  <span>The Green Sheet</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              {/* Stat 3 */}
              <div className="pb-6 border-b border-slate-100 dark:border-slate-800/80 flex flex-col sm:flex-row sm:items-baseline justify-between gap-4">
                <div className="text-5xl sm:text-6xl font-black text-[#0052cc] dark:text-[#3b82f6] tracking-tight shrink-0 w-56 sm:w-64">
                  58%
                </div>
                <div className="flex-1 text-xs sm:text-sm text-slate-700 dark:text-slate-300 pr-4">
                  of UK online merchants believe AI-initiated purchases already reach them.
                </div>
                <a
                  href="https://www.retailgazette.co.uk/blog/2026/05/uk-retailers-warn-over-rising-ai-shopping-risks/"
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium"
                >
                  <span>Retail Gazette</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              {/* Stat 4 */}
              <div className="flex flex-col sm:flex-row sm:items-baseline justify-between gap-4">
                <div className="text-5xl sm:text-6xl font-black text-[#0052cc] dark:text-[#3b82f6] tracking-tight shrink-0 w-56 sm:w-64">
                  281.3M
                </div>
                <div className="flex-1 text-xs sm:text-sm text-slate-700 dark:text-slate-300 pr-4">
                  global chargebacks estimated for 2026, from Mastercard and Datos data.
                </div>
                <a
                  href="https://www.chargeback.io/blog/chargeback-statistics"
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium"
                >
                  <span>Chargeback.io</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

            </div>

            {/* 4 Article Citations Rows */}
            <div className="divide-y divide-slate-100 dark:divide-slate-800/80 pt-6">
              
              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="w-44 shrink-0 text-slate-500 dark:text-slate-400">
                  <div className="font-semibold text-slate-700 dark:text-slate-300">Chargebacks911</div>
                  <div>30 Jun 2026</div>
                </div>
                <div className="flex-1">
                  <div className="font-semibold text-slate-900 dark:text-white">Friendly fraud keeps climbing for large merchants</div>
                  <div className="text-slate-500 dark:text-slate-400">About one in four teams uses five or more separate tools just to investigate disputes.</div>
                </div>
                <a href="https://chargebacks911.com/2026-chargeback-field-report/" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="w-44 shrink-0 text-slate-500 dark:text-slate-400">
                  <div className="font-semibold text-slate-700 dark:text-slate-300">eCommerceNews Asia</div>
                  <div>2 Jul 2026</div>
                </div>
                <div className="flex-1">
                  <div className="font-semibold text-slate-900 dark:text-white">Most merchants have no dedicated dispute team</div>
                  <div className="text-slate-500 dark:text-slate-400">Only about a third report a chargeback lead, and few track the card-network records behind their VAMP ratio.</div>
                </div>
                <a href="https://ecommercenews.asia/story/friendly-fraud-drives-up-merchant-costs-report-finds" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="w-44 shrink-0 text-slate-500 dark:text-slate-400">
                  <div className="font-semibold text-slate-700 dark:text-slate-300">Forkast</div>
                  <div>Analysis</div>
                </div>
                <div className="flex-1">
                  <div className="font-semibold text-slate-900 dark:text-white">Who pays when the AI agent buys wrong?</div>
                  <div className="text-slate-500 dark:text-slate-400">Agent payment protocols do not reallocate liability for non-fraud errors, and a draft EMVCo framework leaves it open.</div>
                </div>
                <a href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              <div className="py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs">
                <div className="w-44 shrink-0 text-slate-500 dark:text-slate-400">
                  <div className="font-semibold text-slate-700 dark:text-slate-300">The Paypers</div>
                  <div>24 Jun 2026</div>
                </div>
                <div className="flex-1">
                  <div className="font-semibold text-slate-900 dark:text-white">Disputes and friendly fraud in the age of agentic commerce</div>
                  <div className="text-slate-500 dark:text-slate-400">A Juniper Research analyst describes a regulatory grey area and a missing source of truth for agent-led purchases.</div>
                </div>
                <a href="https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline shrink-0 inline-flex items-center gap-0.5 font-medium">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

            </div>

          </div>

        </div>
      </section>

      {/* ────────────────── SECTION: HOW IT WORKS ────────────────── */}
      <section id="how-it-works" className="py-20 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto space-y-10 border-t border-slate-100 dark:border-slate-800">
        <div>
          <h2 className="text-3xl sm:text-4xl font-black text-slate-900 dark:text-white tracking-tight">How it works</h2>
          <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1">
            Rules detect. AI investigates. Humans decide.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 pt-4">
          
          <div className="space-y-2 border-t border-slate-200 dark:border-slate-800 pt-4 relative">
            <span className="w-2.5 h-2.5 rounded-full bg-[#0066cc] absolute -top-[5px] left-0" />
            <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-1.5">
              <GoogleIcon name="input" size={16} className="text-blue-600" />
              <span>1. Ingest</span>
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              Signed PayPal webhooks land in a durable inbox. Duplicates are ignored.
            </p>
          </div>

          <div className="space-y-2 border-t border-slate-200 dark:border-slate-800 pt-4 relative">
            <span className="w-2.5 h-2.5 rounded-full bg-[#0066cc] absolute -top-[5px] left-0" />
            <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-1.5">
              <GoogleIcon name="rule" size={16} className="text-blue-600" />
              <span>2. Detect</span>
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              Deterministic rules flag paid-not-marked, amount mismatch and duplicates.
            </p>
          </div>

          <div className="space-y-2 border-t border-slate-200 dark:border-slate-800 pt-4 relative">
            <span className="w-2.5 h-2.5 rounded-full bg-[#0066cc] absolute -top-[5px] left-0" />
            <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-1.5">
              <GoogleIcon name="psychology" size={16} className="text-blue-600" />
              <span>3. Investigate</span>
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              An agent queries PayPal live with read-only tools and cites every record.
            </p>
          </div>

          <div className="space-y-2 border-t border-slate-200 dark:border-slate-800 pt-4 relative">
            <span className="w-2.5 h-2.5 rounded-full bg-[#0066cc] absolute -top-[5px] left-0" />
            <h3 className="font-bold text-sm text-slate-900 dark:text-white flex items-center gap-1.5">
              <GoogleIcon name="verified_user" size={16} className="text-blue-600" />
              <span>4. Approve</span>
            </h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              It proposes a safe action. An operator approves and the audit log records it.
            </p>
          </div>

        </div>
      </section>

      {/* ────────────────── SECTION: THE PRODUCT BANNER CARD ────────────────── */}
      <section id="product" className="py-12 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto">
        <div className="bg-gradient-to-br from-[#0052cc] via-[#005ce6] to-[#0047b3] text-white rounded-3xl p-8 sm:p-14 lg:p-16 shadow-2xl relative overflow-hidden">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-14 items-center">
            
            {/* Left Content */}
            <div className="lg:col-span-6 space-y-6">
              <h2 className="text-4xl sm:text-5xl font-black tracking-tight text-white leading-[1.08]">
                One map. One<br />
                investigator. Proof<br />
                for every claim.
              </h2>

              <p className="text-xs sm:text-sm text-blue-100 leading-relaxed font-normal">
                Open any broken payment and see everything that touched it. Click Investigate and watch the agent query PayPal and your records, then answer in plain language.
              </p>

              <div className="space-y-4 pt-2 text-xs sm:text-sm">
                <div className="space-y-0.5">
                  <h3 className="font-bold text-white text-sm flex items-center gap-1.5">
                    <GoogleIcon name="link" size={16} className="text-blue-200" />
                    <span>Every statement cites a record</span>
                  </h3>
                  <p className="text-blue-100/90 text-xs">Links go to the exact capture, event or order.</p>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-white text-sm flex items-center gap-1.5">
                    <GoogleIcon name="verified" size={16} className="text-blue-200" />
                    <span>A verifier checks the AI</span>
                  </h3>
                  <p className="text-blue-100/90 text-xs">Record ids and amounts are validated before you see them.</p>
                </div>
                <div className="space-y-0.5">
                  <h3 className="font-bold text-white text-sm flex items-center gap-1.5">
                    <GoogleIcon name="help_outline" size={16} className="text-blue-200" />
                    <span>It admits when it does not know</span>
                  </h3>
                  <p className="text-blue-100/90 text-xs">"Cause not determined" beats a confident guess.</p>
                </div>
              </div>
            </div>

            {/* Right Card Mockup */}
            <div className="lg:col-span-6 relative">
              <div className="bg-[#0b121f] rounded-2xl border border-slate-700/60 p-5 shadow-2xl relative space-y-4 text-xs">
                
                {/* Header with Case PR-0142 & Investigating badge */}
                <div className="flex items-center justify-between pb-2 border-b border-slate-800">
                  <div className="flex items-center space-x-2 text-[10px] text-slate-400 font-mono">
                    <span className="w-2 h-2 rounded-full bg-slate-600 inline-block" />
                    <span className="w-2 h-2 rounded-full bg-slate-600 inline-block" />
                    <span className="w-2 h-2 rounded-full bg-slate-600 inline-block" />
                    <span className="ml-2">Case PR-0142</span>
                  </div>
                  <span className="px-2.5 py-0.5 rounded-full border border-emerald-500/60 text-emerald-400 text-[10px] flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                    Investigating
                  </span>
                </div>

                {/* Graph mockup canvas */}
                <div className="relative w-full h-56 bg-[#070d18] rounded-xl border border-slate-800 p-3 overflow-hidden flex items-center justify-center">
                  <svg className="absolute inset-0 w-full h-full pointer-events-none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M 60 110 L 120 70" stroke="#334155" strokeWidth="1.5" fill="none" />
                    <path d="M 60 110 L 120 150" stroke="#334155" strokeWidth="1.5" fill="none" />
                    <path d="M 150 90 L 150 130" stroke="#334155" strokeWidth="1.5" fill="none" />
                    <path d="M 180 70 L 240 100" stroke="#f59e0b" strokeWidth="1.5" strokeDasharray="3 3" fill="none" />
                  </svg>

                  <div className="absolute left-2 top-24 bg-[#121a2a] border border-slate-700 rounded p-1.5 text-[9px] text-white">
                    PayPal order
                  </div>
                  <div className="absolute left-28 top-8 bg-[#121a2a] border border-cyan-400 rounded p-1.5 text-[9px] text-cyan-300">
                    Capture COMPLETED, $84.50
                  </div>
                  <div className="absolute left-28 bottom-8 bg-[#121a2a] border border-slate-700 rounded p-1.5 text-[9px] text-white">
                    Webhook delivered twice
                  </div>
                  <div className="absolute right-4 top-16 bg-[#121a2a] border border-amber-500 rounded p-1.5 text-[9px] text-amber-300">
                    Shop order #10482 still PENDING
                  </div>
                </div>

                {/* Pill tool latency buttons */}
                <div className="flex items-center space-x-2 pt-1 font-mono text-[9px]">
                  <span className="px-2 py-0.5 rounded-full border border-blue-500/40 bg-blue-950/40 text-blue-300">
                    getPaypalOrder 140 ms
                  </span>
                  <span className="px-2 py-0.5 rounded-full border border-blue-500/40 bg-blue-950/40 text-blue-300">
                    listWebhookEvents 64 ms
                  </span>
                </div>

                {/* Floating Report Card */}
                <div className="absolute -bottom-6 -right-3 sm:-right-6 w-72 bg-white text-slate-900 rounded-xl p-4 shadow-2xl border border-slate-100 space-y-2 z-20">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-xs flex items-center gap-1.5">
                      <GoogleIcon name="summarize" size={15} className="text-blue-600" />
                      Report
                    </span>
                    <span className="px-2 py-0.5 rounded-full border border-emerald-500/60 text-emerald-700 bg-emerald-50 text-[10px] font-medium flex items-center gap-1">
                      <GoogleIcon name="check" size={11} className="text-emerald-600" />
                      Verified
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-700">
                    <strong>Conflict:</strong> shop order still PENDING after capture.
                  </p>
                  <div className="flex items-center space-x-2 pt-1">
                    <Link
                      to="/queue"
                      className="px-3 py-1 rounded-md bg-[#0066cc] hover:bg-blue-700 active:scale-[0.97] text-white font-medium text-xs transition-all cursor-pointer shadow-sm"
                    >
                      Approve sync
                    </Link>
                    <button
                      type="button"
                      className="px-3 py-1 rounded-md border border-slate-200 text-slate-700 hover:bg-slate-100 active:scale-[0.97] text-xs transition-all cursor-pointer"
                    >
                      Reject
                    </button>
                  </div>
                </div>

              </div>
            </div>

          </div>
        </div>
      </section>

      {/* ────────────────── SECTION: THREE PAYMENTS THAT GO WRONG ────────────────── */}
      <section className="py-20 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto space-y-10 border-t border-slate-100 dark:border-slate-800">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-16 items-start">
          
          <div className="lg:col-span-5 space-y-3">
            <h2 className="text-4xl font-black text-slate-900 dark:text-white tracking-tight leading-[1.08]">
              Three payments<br />
              that go wrong
            </h2>
            <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 leading-relaxed pt-1">
              PayPal, the order system, refunds and delivery each hold a piece. When they disagree, someone rebuilds the story by hand.
            </p>
          </div>

          <div className="lg:col-span-7 divide-y divide-slate-100 dark:divide-slate-800/80 text-xs sm:text-sm">
            <div className="pb-6 space-y-1">
              <h3 className="font-bold text-slate-900 dark:text-white text-sm flex items-center gap-1.5">
                <GoogleIcon name="error_outline" size={16} className="text-amber-500" />
                <span>Paid, but marked unpaid</span>
              </h3>
              <p className="text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                A customer pays $84.50. The server crashes mid-webhook and order #10482 stays pending. Asking them to pay again would double-charge them.
              </p>
            </div>

            <div className="py-6 space-y-1">
              <h3 className="font-bold text-slate-900 dark:text-white text-sm flex items-center gap-1.5">
                <GoogleIcon name="repeat" size={16} className="text-blue-500" />
                <span>The webhook that arrived twice</span>
              </h3>
              <p className="text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                Without idempotency the order ships twice. PayResolve proves exactly one business action ran.
              </p>
            </div>

            <div className="pt-6 space-y-1">
              <h3 className="font-bold text-slate-900 dark:text-white text-sm flex items-center gap-1.5">
                <GoogleIcon name="currency_exchange" size={16} className="text-indigo-500" />
                <span>Where is my refund?</span>
              </h3>
              <p className="text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                Requested, pending and completed look identical to a customer. It separates them before anyone refunds twice.
              </p>
            </div>
          </div>

        </div>
      </section>

      {/* ────────────────── SECTION: WHY WE CHOSE THIS PROBLEM ────────────────── */}
      <section className="py-20 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto space-y-10 border-t border-slate-100 dark:border-slate-800">
        <h2 className="text-4xl font-black text-slate-900 dark:text-white tracking-tight">
          Why we chose this problem
        </h2>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-16 items-start">
          
          {/* Judging Criteria Table */}
          <div className="lg:col-span-7 overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400">
                  <th className="py-2.5 pr-4 font-normal text-xs">Judging criterion</th>
                  <th className="py-2.5 pl-4 font-normal text-xs">How PayResolve answers it</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60">
                <tr>
                  <td className="py-4 pr-4 font-semibold text-slate-900 dark:text-white text-xs align-top">
                    Technological implementation
                  </td>
                  <td className="py-4 pl-4 text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                    Real Sandbox capture, signed webhooks, idempotency, tool-calling AI with a verifier
                  </td>
                </tr>
                <tr>
                  <td className="py-4 pr-4 font-semibold text-slate-900 dark:text-white text-xs align-top">Design</td>
                  <td className="py-4 pl-4 text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                    Queue, case, graph, approve: one complete operator workflow
                  </td>
                </tr>
                <tr>
                  <td className="py-4 pr-4 font-semibold text-slate-900 dark:text-white text-xs align-top">Potential impact</td>
                  <td className="py-4 pl-4 text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                    Daily merchant pain with real cost: double charges, double refunds, missed disputes
                  </td>
                </tr>
                <tr>
                  <td className="py-4 pr-4 font-semibold text-slate-900 dark:text-white text-xs align-top">Innovation</td>
                  <td className="py-4 pl-4 text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                    Provenance and safe actions, not a risk score or a chatbot
                  </td>
                </tr>
                <tr>
                  <td className="py-4 pr-4 font-semibold text-slate-900 dark:text-white text-xs align-top">Presentation</td>
                  <td className="py-4 pl-4 text-slate-600 dark:text-slate-400 text-xs leading-relaxed">
                    One story: paid, but the system says unpaid
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Right: Why Now */}
          <div className="lg:col-span-5 space-y-3">
            <h3 className="font-bold text-sm text-slate-900 dark:text-white">Why now</h3>
            <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
              AI agents are starting to buy things for customers, and nobody has settled who is liable when they get it wrong. Merchants will need records they can trust. Read the{' '}
              <a href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5 font-medium">
                <span>Forkast analysis</span>
                <GoogleIcon name="arrow_outward" size={12} />
              </a>
              , and why agent-led disputes lack a source of truth in{' '}
              <a href="https://www.chargeflow.io/blog/ai-agent-chargeback-liability" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5 font-medium">
                <span>Chargeflow</span>
                <GoogleIcon name="arrow_outward" size={12} />
              </a>{' '}
              and{' '}
              <a href="https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5 font-medium">
                <span>The Paypers</span>
                <GoogleIcon name="arrow_outward" size={12} />
              </a>
              .
            </p>
          </div>

        </div>
      </section>

      {/* ────────────────── SECTION: SOURCES AND REFERENCES ────────────────── */}
      <section id="sources" className="py-20 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto space-y-6 border-t border-slate-100 dark:border-slate-800">
        <div>
          <h2 className="text-4xl font-black text-slate-900 dark:text-white tracking-tight">
            Sources and references
          </h2>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
            Every figure on this page comes from the articles below.
          </p>
        </div>

        <ol className="space-y-3 text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
          <li className="break-all">
            1. Chargebacks911, 2026 Chargeback Field Report, 30 Jun 2026{' '}
            <a href="https://chargebacks911.com/2026-chargeback-field-report/" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://chargebacks911.com/2026-chargeback-field-report/
            </a>
          </li>
          <li className="break-all">
            2. The Green Sheet, Chargebacks are quietly raising prices, 28 Jul 2026{' '}
            <a href="https://greensheet.com/newswire&article_id=64300" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://greensheet.com/newswire&article_id=64300
            </a>
          </li>
          <li className="break-all">
            3. eCommerceNews Asia, Friendly fraud drives up merchant costs, 2 Jul 2026{' '}
            <a href="https://ecommercenews.asia/story/friendly-fraud-drives-up-merchant-costs-report-finds" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://ecommercenews.asia/story/friendly-fraud-drives-up-merchant-costs-report-finds
            </a>
          </li>
          <li className="break-all">
            4. Retail Gazette, UK retailers raise concerns over agentic AI shopping risks, May 2026{' '}
            <a href="https://www.retailgazette.co.uk/blog/2026/05/uk-retailers-warn-over-rising-ai-shopping-risks/" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://www.retailgazette.co.uk/blog/2026/05/uk-retailers-warn-over-rising-ai-shopping-risks/
            </a>
          </li>
          <li className="break-all">
            5. Forkast, The agentic commerce liability gap{' '}
            <a href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/
            </a>
          </li>
          <li className="break-all">
            6. The Paypers, Disputes and friendly fraud in the age of agentic commerce, 24 Jun 2026{' '}
            <a href="https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce
            </a>
          </li>
          <li className="break-all">
            7. Chargeflow, AI agent chargeback liability, 7 Jul 2026{' '}
            <a href="https://www.chargeflow.io/blog/ai-agent-chargeback-liability" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://www.chargeflow.io/blog/ai-agent-chargeback-liability
            </a>
          </li>
          <li className="break-all">
            8. Chargeback.io, Chargeback statistics 2026{' '}
            <a href="https://www.chargeback.io/blog/chargeback-statistics" target="_blank" rel="noreferrer" className="text-blue-600 dark:text-blue-400 hover:underline">
              https://www.chargeback.io/blog/chargeback-statistics
            </a>
          </li>
        </ol>
      </section>

      {/* ────────────────── SECTION: BOTTOM CTA BANNER ────────────────── */}
      <section className="py-12 px-6 sm:px-8 lg:px-12 max-w-[1240px] mx-auto">
        <div className="bg-gradient-to-r from-[#0052cc] via-[#005ce6] to-[#0047b3] text-white rounded-3xl p-10 sm:p-14 lg:p-16 shadow-2xl space-y-6">
          <div className="max-w-2xl space-y-2">
            <h2 className="text-4xl sm:text-5xl font-black tracking-tight text-white leading-[1.08]">
              See a broken payment<br />
              explained in under a<br />
              minute.
            </h2>
            <p className="text-xs sm:text-sm text-blue-100 pt-1">
              Real Sandbox payments. Real webhooks. No real money.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-4">
            <Link
              to="/queue"
              className="bg-white text-slate-900 px-5 py-2.5 rounded-lg font-semibold text-xs sm:text-sm shadow-md hover:bg-slate-100 active:scale-[0.97] transition-all cursor-pointer"
            >
              Open workspace
            </Link>
            <Link
              to="/signin"
              className="border border-white/40 bg-transparent text-white hover:bg-white/10 active:scale-[0.97] px-5 py-2.5 rounded-lg font-medium text-xs sm:text-sm transition-all cursor-pointer"
            >
              Sign in
            </Link>
          </div>
        </div>
      </section>

      {/* ────────────────── FOOTER ────────────────── */}
      <footer className="bg-[#e9f1f9] dark:bg-[#050911] border-t border-slate-200/80 dark:border-slate-800/80 pt-16 pb-12 px-6 sm:px-8 lg:px-12 text-xs text-slate-600 dark:text-slate-400">
        <div className="max-w-[1240px] mx-auto space-y-12">
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 gap-8">
            
            {/* Col 1 & 2: Brand */}
            <div className="lg:col-span-2 space-y-4">
              <Link to="/" className="inline-flex items-center space-x-2">
                <PayResolveMark size={24} />
                <span className="font-bold text-base text-slate-900 dark:text-white flex items-center gap-1">
                  PayResolve
                  <span className="text-[10px] bg-blue-500 text-white px-1 py-0.2 rounded font-mono">
                    AI
                  </span>
                </span>
              </Link>
              <p className="text-xs text-slate-500 dark:text-slate-400 max-w-xs leading-relaxed">
                Investigate PayPal payment exceptions with evidence you can trust. AI proposes, humans approve.
              </p>
              <div>
                <span className="inline-flex items-center space-x-1.5 px-3 py-1 rounded-full border border-emerald-500/50 bg-white dark:bg-[#071d18] text-emerald-700 dark:text-emerald-400 text-[11px] font-medium shadow-xs">
                  <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                  <span>Sandbox only. No real money.</span>
                </span>
              </div>
            </div>

            {/* Col 3: Product */}
            <div className="space-y-3">
              <h4 className="font-semibold text-slate-900 dark:text-white text-xs">Product</h4>
              <ul className="space-y-2 text-xs">
                <li><Link to="/queue" className="hover:underline">Exception queue</Link></li>
                <li><Link to="/case/PR-0142" className="hover:underline">Case workspace</Link></li>
                <li><Link to="/events" className="hover:underline">Event inbox</Link></li>
                <li><Link to="/shop" className="hover:underline">Demo shop</Link></li>
                <li><Link to="/actions" className="hover:underline">Actions</Link></li>
                <li><Link to="/benchmark" className="hover:underline">Benchmark</Link></li>
              </ul>
            </div>

            {/* Col 4: Project */}
            <div className="space-y-3">
              <h4 className="font-semibold text-slate-900 dark:text-white text-xs">Project</h4>
              <ul className="space-y-2 text-xs">
                <li><a href="#how-it-works" className="hover:underline">Product requirements</a></li>
                <li><a href="#how-it-works" className="hover:underline">Architecture</a></li>
                <li><a href="#how-it-works" className="hover:underline">Build phases</a></li>
                <li><a href="#how-it-works" className="hover:underline">Engineering rules</a></li>
                <li><a href="https://github.com/Skedare240507/PayResolve-AI" target="_blank" rel="noreferrer" className="hover:underline">GitHub repository</a></li>
              </ul>
            </div>

            {/* Col 5: Hackathon */}
            <div className="space-y-3">
              <h4 className="font-semibold text-slate-900 dark:text-white text-xs">Hackathon</h4>
              <ul className="space-y-2 text-xs">
                <li>
                  <a href="https://devpost.com" target="_blank" rel="noreferrer" className="hover:underline inline-flex items-center gap-0.5">
                    <span>PayPal AI Hackathon</span>
                    <GoogleIcon name="arrow_outward" size={13} />
                  </a>
                </li>
                <li>
                  <a href="https://devpost.com" target="_blank" rel="noreferrer" className="hover:underline inline-flex items-center gap-0.5">
                    <span>Official rules</span>
                    <GoogleIcon name="arrow_outward" size={13} />
                  </a>
                </li>
                <li>
                  <a href="https://devpost.com" target="_blank" rel="noreferrer" className="hover:underline inline-flex items-center gap-0.5">
                    <span>Project gallery</span>
                    <GoogleIcon name="arrow_outward" size={13} />
                  </a>
                </li>
              </ul>
            </div>

            {/* Col 6: Research */}
            <div className="space-y-3">
              <h4 className="font-semibold text-slate-900 dark:text-white text-xs">Research</h4>
              <ul className="space-y-2 text-xs">
                <li>
                  <a href="https://chargebacks911.com/2026-chargeback-field-report/" target="_blank" rel="noreferrer" className="hover:underline inline-flex items-center gap-0.5">
                    <span>2026 Chargeback Field Report</span>
                    <GoogleIcon name="arrow_outward" size={13} />
                  </a>
                </li>
                <li>
                  <a href="https://www.chargeback.io/blog/chargeback-statistics" target="_blank" rel="noreferrer" className="hover:underline inline-flex items-center gap-0.5">
                    <span>Chargeback statistics</span>
                    <GoogleIcon name="arrow_outward" size={13} />
                  </a>
                </li>
                <li>
                  <a href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer" className="hover:underline inline-flex items-center gap-0.5">
                    <span>Agentic liability gap</span>
                    <GoogleIcon name="arrow_outward" size={13} />
                  </a>
                </li>
              </ul>
            </div>

          </div>

          {/* Bottom Copyright & Disclaimer */}
          <div className="pt-8 border-t border-slate-200 dark:border-slate-800/80 space-y-3 text-[11px] text-slate-500 dark:text-slate-400">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>&copy; 2026 PayResolve AI. MIT licensed. Built for the PayPal AI Hackathon.</div>
              <div className="flex items-center space-x-3">
                <a href="#sources" className="hover:underline">Privacy</a>
                <span>&middot;</span>
                <a href="#sources" className="hover:underline">Security</a>
                <span>&middot;</span>
                <a href="https://github.com/Skedare240507/PayResolve-AI/blob/main/LICENSE" target="_blank" rel="noreferrer" className="hover:underline">Licence</a>
                <span>&middot;</span>
                <Link to="/status" className="hover:underline">Status</Link>
              </div>
            </div>
            <p className="text-[10px] text-slate-400 dark:text-slate-500 max-w-4xl">
              PayResolve AI is an independent hackathon project. It is not affiliated with, endorsed by or sponsored by PayPal, Inc. PayPal is a trademark of PayPal, Inc. All payment data shown is synthetic Sandbox data.
            </p>
          </div>

        </div>
      </footer>

    </div>
  );
}