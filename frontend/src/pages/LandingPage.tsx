import { Link } from 'react-router-dom';
import { PayResolveMark } from '../components/PayResolveLogo';
import { ThemeToggle } from '../components/ThemeToggle';
import { GoogleIcon } from '../components/GoogleIcon';
import { InfoTip } from '../components/InfoTip';

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-slate-50 dark:bg-midnight-950 text-slate-900 dark:text-slate-200 font-sans antialiased selection:bg-blue-100 dark:selection:bg-blue-600/30 selection:text-blue-900 dark:selection:text-white transition-colors duration-200">
      
      {/* ────────────────── HEADER ────────────────── */}
      <header className="sticky top-0 z-50 backdrop-blur-xl bg-white/90 dark:bg-midnight-950/80 border-b border-slate-200/80 dark:border-white/[0.07] shadow-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          
          {/* Brand Logo */}
          <Link to="/" className="flex items-center gap-2.5 group">
            <div className="w-8 h-8 rounded-lg bg-blue-600 dark:bg-gradient-to-tr dark:from-blue-600 dark:via-indigo-600 dark:to-sky-400 p-[1px] shadow-sm flex items-center justify-center text-white">
              <PayResolveMark size={16} />
            </div>
            <div className="flex items-center gap-1.5">
              <span className="font-bold tracking-tight text-slate-900 dark:text-white text-lg">PayResolve</span>
              <span className="px-1.5 py-0.5 rounded text-[10px] font-mono tracking-wider font-semibold uppercase bg-blue-50 dark:bg-blue-500/20 text-blue-700 dark:text-sky-400 border border-blue-200 dark:border-blue-500/30">
                AI
              </span>
            </div>
          </Link>

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-slate-600 dark:text-slate-300">
            <a href="#market-risk" className="hover:text-blue-600 dark:hover:text-white transition-colors">Market risk</a>
            <a href="#how-it-works" className="hover:text-blue-600 dark:hover:text-white transition-colors">How it works</a>
            <a href="#the-product" className="hover:text-blue-600 dark:hover:text-white transition-colors">The product</a>
            <a href="#sources" className="hover:text-blue-600 dark:hover:text-white transition-colors">Sources</a>
          </nav>

          {/* Action items & Light toggle & Workspace CTA */}
          <div className="flex items-center gap-3">
            <ThemeToggle variant="nav" />
            <Link
              to="/signin"
              className="text-sm font-medium text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white px-3 py-1.5 rounded-lg transition-colors"
            >
              Sign in
            </Link>
            <Link
              to="/queue"
              className="relative group inline-flex items-center justify-center text-xs sm:text-sm font-semibold px-4 py-2 rounded-full bg-slate-900 text-white dark:bg-white dark:text-slate-950 hover:bg-slate-800 dark:hover:bg-slate-100 transition shadow tracking-tight cursor-pointer"
            >
              <span>Open workspace</span>
            </Link>
          </div>

        </div>
      </header>

      {/* ────────────────── HERO SECTION ────────────────── */}
      <section className="relative pt-12 pb-24 md:pt-20 md:pb-32 overflow-hidden bg-slate-50 dark:bg-midnight-950 border-b border-slate-200/60 dark:border-white/[0.06]">
        {/* Atmospheric flares for light and dark modes */}
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[700px] h-[450px] bg-blue-100/60 dark:bg-blue-600/15 blur-[120px] rounded-full pointer-events-none" />
        <div className="absolute top-1/3 left-1/4 w-[350px] h-[350px] bg-sky-100/70 dark:bg-indigo-500/10 blur-[90px] rounded-full pointer-events-none" />

        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
          
          {/* Hackathon Sandbox Pill Tag */}
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white dark:bg-slate-900/80 border border-slate-200 dark:border-slate-700/60 text-xs font-mono text-slate-600 dark:text-slate-400 mb-8 shadow-xs">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span className="text-slate-900 dark:text-slate-300 font-semibold">PayPal AI Hackathon 2026.</span> Sandbox build.
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-start">
            
            {/* Left Hero Column: Copy & Actions */}
            <div className="lg:col-span-5 flex flex-col justify-center">
              <h1 className="text-4xl sm:text-5xl xl:text-6xl font-extrabold tracking-tight text-slate-950 dark:text-white leading-[1.12] mb-6">
                Customer <br className="hidden sm:inline" />
                says paid.<br />
                System says <br className="hidden sm:inline" />
                <span className="text-slate-400 italic font-serif font-normal">unpaid.</span>
              </h1>
              <p className="text-base sm:text-lg text-slate-600 dark:text-slate-300 leading-relaxed font-normal mb-8 max-w-lg">
                PayResolve puts every PayPal event, order, refund and shipment on one evidence map, and then lets an AI Investigator explain what is confirmed and what is missing. You approve every fix.
              </p>
              
              <div className="flex flex-wrap items-center gap-4">
                <Link
                  to="/queue"
                  className="px-6 py-3 rounded-full text-sm font-semibold bg-blue-600 text-white hover:bg-blue-700 dark:bg-white dark:text-slate-950 dark:hover:bg-slate-100 transition shadow-sm flex items-center gap-2 cursor-pointer"
                >
                  <span>Open workspace</span>
                  <GoogleIcon name="arrow_forward" size={16} />
                </Link>
                <a
                  href="#how-it-works"
                  className="px-6 py-3 rounded-full text-sm font-semibold text-slate-700 dark:text-slate-200 border border-slate-300 dark:border-slate-700 hover:border-slate-400 dark:hover:border-slate-500 bg-white dark:bg-slate-900/40 hover:bg-slate-50 dark:hover:bg-slate-800/40 transition shadow-xs"
                >
                  See how it works
                </a>
              </div>

              {/* Subtle Trust Footnote */}
              <div className="mt-8 flex items-center gap-3 text-xs text-slate-500 dark:text-slate-400 font-mono">
                <span className="flex h-2 w-2 relative">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-500 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-600 dark:bg-blue-500" />
                </span>
                <span>Deterministic engine + verifiable LLM audit loop</span>
              </div>
            </div>

            {/* Right Hero Column: Interactive Evidence Graph Canvas visual */}
            <div className="lg:col-span-7">
              <div className="relative rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200/90 dark:border-slate-700/60 p-5 sm:p-7 shadow-xl">
                
                {/* Window header & breadcrumb */}
                <div className="flex items-center justify-between pb-4 mb-6 border-b border-slate-100 dark:border-slate-800 text-xs font-mono text-slate-500 dark:text-slate-400">
                  <div className="flex items-center gap-2">
                    <div className="flex gap-1.5">
                      <div className="w-2.5 h-2.5 rounded-full bg-rose-400 dark:bg-rose-500/80" />
                      <div className="w-2.5 h-2.5 rounded-full bg-amber-400 dark:bg-amber-500/80" />
                      <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 dark:bg-emerald-500/80" />
                    </div>
                    <span className="ml-2 font-semibold text-slate-800 dark:text-slate-300">Case PR-0142: Evidence graph</span>
                  </div>
                  <div className="flex items-center gap-2 bg-slate-100 dark:bg-slate-800/70 text-slate-700 dark:text-slate-300 px-2.5 py-1 rounded-md text-[11px] font-semibold border border-slate-200 dark:border-slate-700/60">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 dark:bg-emerald-400" />
                    <span>Live Sandbox</span>
                  </div>
                </div>

                {/* Crafted Interactive Flow Nodes with SVG connectors */}
                <div className="relative min-h-[380px] w-full rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200 dark:border-slate-800/70 p-4 sm:p-6 overflow-hidden">
                  
                  {/* SVG Connectors & Flow Lines */}
                  <svg className="absolute inset-0 w-full h-full pointer-events-none stroke-slate-300 dark:stroke-slate-600/70" fill="none" strokeWidth="1.75">
                    <path className="stroke-indigo-400" d="M 120 70 C 180 70, 200 45, 270 45" strokeDasharray="4 4" />
                    <path className="stroke-amber-400" d="M 400 45 C 440 45, 450 100, 480 100" />
                    <path className="stroke-slate-300 dark:stroke-slate-500" d="M 120 70 C 180 70, 220 180, 290 180" />
                    <path className="stroke-slate-300 dark:stroke-slate-600" d="M 330 75 C 330 160, 200 240, 140 270" strokeDasharray="2 3" />
                  </svg>

                  {/* Node 1: PayPal Order */}
                  <div className="absolute top-10 left-3 sm:left-4 z-10 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-700/80 rounded-lg p-2.5 px-3 shadow-xs">
                    <div className="text-[10px] uppercase font-mono tracking-wider text-slate-500 dark:text-slate-400 font-semibold">PayPal order</div>
                    <div className="text-xs font-semibold text-emerald-600 dark:text-emerald-400 flex items-center gap-1.5 mt-0.5">
                      <GoogleIcon name="check_circle" size={14} className="text-emerald-500" />
                      <span>approved</span>
                    </div>
                  </div>

                  {/* Node 2: Capture Completed (Central) */}
                  <div className="absolute top-4 left-56 sm:left-64 z-10 bg-white dark:bg-slate-900 border-2 border-blue-600 dark:border-blue-500/60 rounded-lg p-3 shadow-md">
                    <div className="flex items-center justify-between gap-3 text-[10px] font-mono text-slate-500 dark:text-slate-400 mb-1">
                      <span className="font-bold text-slate-700 dark:text-slate-300">CAPTURE</span>
                      <span className="text-emerald-600 dark:text-emerald-400 font-bold">$84.50</span>
                    </div>
                    <div className="text-xs font-bold text-slate-900 dark:text-white">COMPLETED, $84.50</div>
                  </div>

                  {/* Node 3: Missing paid update tag */}
                  <div className="absolute top-16 left-64 sm:left-80 z-20">
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono font-semibold bg-amber-50 dark:bg-amber-500/20 text-amber-700 dark:text-amber-300 border border-amber-300 dark:border-amber-500/40 shadow-xs">
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-ping" />
                      <span>missing: paid update</span>
                    </span>
                  </div>

                  {/* Node 4: Shop order still pending */}
                  <div className="absolute top-20 right-3 sm:right-6 z-10 bg-white dark:bg-slate-900/95 border border-amber-300 dark:border-amber-500/60 rounded-lg p-3 shadow-xs">
                    <div className="text-[10px] uppercase font-mono text-slate-500 dark:text-slate-400 font-semibold">Shop order #10482</div>
                    <div className="text-xs font-bold text-amber-600 dark:text-amber-400 flex items-center gap-1 mt-0.5">
                      <GoogleIcon name="schedule" size={13} className="text-amber-500" />
                      <span>still PENDING</span>
                    </div>
                  </div>

                  {/* Node 5: Webhook delivered twice */}
                  <div className="absolute top-36 left-48 sm:left-56 z-10 bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-700 rounded-lg px-3 py-2 shadow-xs">
                    <div className="text-[10px] font-mono text-slate-500 dark:text-slate-400 font-semibold">Webhook</div>
                    <div className="text-xs font-medium text-slate-800 dark:text-slate-200">delivered twice</div>
                  </div>

                  {/* Node 6 & 7: Micro pill tokens */}
                  <div className="absolute bottom-4 left-4 flex gap-2 text-[10px] font-mono text-slate-500 dark:text-slate-400">
                    <span className="px-2 py-1 rounded bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 shadow-xs">getCaptureDetails 212 ms</span>
                    <span className="px-2 py-1 rounded bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 shadow-xs">getShopOrder 88 ms</span>
                  </div>

                  {/* Floating AI Investigator Card Overlaid on Diagram */}
                  <div className="absolute -bottom-2 -right-2 sm:bottom-4 sm:right-4 z-30 max-w-xs bg-white dark:bg-slate-900/95 border border-slate-200 dark:border-white/20 rounded-xl p-4 shadow-xl backdrop-blur-md">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-1.5 text-xs font-bold text-slate-900 dark:text-white">
                        <div className="w-2 h-2 rounded-full bg-blue-600 animate-pulse" />
                        <span>AI Investigator</span>
                      </div>
                      <span className="text-[10px] font-mono font-semibold px-2 py-0.5 rounded-full bg-emerald-50 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-500/30">
                        2 of 3 verified
                      </span>
                    </div>
                    <div className="space-y-1.5 text-[11px] text-slate-600 dark:text-slate-300">
                      <div className="flex items-start gap-1.5">
                        <span className="text-emerald-600 dark:text-emerald-400 font-bold">✓</span>
                        <span><strong className="text-slate-900 dark:text-slate-200">Confirmed:</strong> capture COMPLETED, $84.50.</span>
                      </div>
                      <div className="flex items-start gap-1.5">
                        <span className="text-rose-600 dark:text-rose-400 font-bold">✕</span>
                        <span><strong className="text-slate-900 dark:text-slate-200">Missing:</strong> status update after the webhook.</span>
                      </div>
                      <div className="flex items-start gap-1.5 pt-1.5 border-t border-slate-100 dark:border-slate-800 text-[10.5px]">
                        <span className="text-blue-600 dark:text-blue-400 font-bold">→</span>
                        <span className="text-slate-600 dark:text-slate-300"><strong className="text-blue-700 dark:text-sky-400">Proposed:</strong> sync order status. Awaiting approval.</span>
                      </div>
                    </div>
                  </div>

                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* ────────────────── PARTNER ECOSYSTEM ────────────────── */}
      <section className="border-b border-slate-200 dark:border-white/[0.08] bg-white dark:bg-midnight-900/50 py-12 relative">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="text-center mb-8">
            <p className="text-[11px] font-mono uppercase tracking-widest text-slate-500 dark:text-slate-400 font-semibold">
              PAYPAL AI HACKATHON PARTNER ECOSYSTEM
            </p>
            <h2 className="text-xl sm:text-2xl font-bold text-slate-900 dark:text-white mt-1">
              Built with industry-leading developer platforms
            </h2>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <div className="bg-slate-50 dark:bg-midnight-950/70 border border-slate-200/90 dark:border-slate-800/90 rounded-xl p-4 flex flex-col justify-between hover:border-slate-300 dark:hover:border-slate-700 transition shadow-2xs">
              <div className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-blue-600 dark:text-blue-400 font-black">P</span> PayPal
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-mono leading-relaxed">
                Orders v2, server captures &amp; webhook verification
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/70 border border-slate-200/90 dark:border-slate-800/90 rounded-xl p-4 flex flex-col justify-between hover:border-slate-300 dark:hover:border-slate-700 transition shadow-2xs">
              <div className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-red-500 dark:text-rose-400 font-bold">▦</span> AG Grid &amp; Studio
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-mono leading-relaxed">
                Operations dashboard, custom widgets &amp; queue
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/70 border border-slate-200/90 dark:border-slate-800/90 rounded-xl p-4 flex flex-col justify-between hover:border-slate-300 dark:hover:border-slate-700 transition shadow-2xs">
              <div className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-amber-500 dark:text-amber-400 font-bold">⚡</span> Groq
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-mono leading-relaxed">
                Llama 3.3 70B ultra-fast primary text router
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/70 border border-slate-200/90 dark:border-slate-800/90 rounded-xl p-4 flex flex-col justify-between hover:border-slate-300 dark:hover:border-slate-700 transition shadow-2xs">
              <div className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-cyan-600 dark:text-cyan-400 font-bold">✦</span> Google Gemini
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-mono leading-relaxed">
                Gemini 2.0 Flash multimodal evidence extraction
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/70 border border-slate-200/90 dark:border-slate-800/90 rounded-xl p-4 flex flex-col justify-between hover:border-slate-300 dark:hover:border-slate-700 transition shadow-2xs">
              <div className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-emerald-600 dark:text-emerald-400 font-bold">▲</span> Render
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-mono leading-relaxed">
                Blueprint deploy, Dockerfile &amp; health checks
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/70 border border-slate-200/90 dark:border-slate-800/90 rounded-xl p-4 flex flex-col justify-between hover:border-slate-300 dark:hover:border-slate-700 transition shadow-2xs">
              <div className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
                <span className="text-orange-500 dark:text-orange-400 font-bold">⟲</span> Postman
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-2 font-mono leading-relaxed">
                End-to-end API test collections &amp; automation
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── MARKET RISK & STATS ────────────────── */}
      <section className="py-20 md:py-28 relative bg-slate-50 dark:bg-midnight-950" id="market-risk">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-start mb-16">
            
            {/* Left Editorial Commentary */}
            <div className="lg:col-span-5">
              <h2 className="text-3xl sm:text-4xl md:text-5xl font-extrabold text-slate-950 dark:text-white tracking-tight leading-tight">
                Disputes are <br className="hidden sm:inline" />
                growing. The <br className="hidden sm:inline" />
                evidence is <br className="hidden sm:inline" />
                scattered.
              </h2>
              <p className="text-slate-600 dark:text-slate-400 text-sm sm:text-base leading-relaxed mt-6">
                Recent industry research points one way: more disputes, thin teams, and AI-initiated purchases with no settled rules. These are vendor and trade-body surveys, quoted as published.
              </p>
            </div>

            {/* Right Numeric Metrics Grid */}
            <div className="lg:col-span-7 space-y-4">
              <div className="p-6 rounded-2xl bg-white dark:bg-midnight-900/60 border border-slate-200 dark:border-slate-800/90 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-2">
                  <div className="text-4xl sm:text-5xl font-extrabold text-blue-600 dark:text-sky-400 font-mono tracking-tight">
                    83.4%
                  </div>
                  <InfoTip metricId="landing.friendlyFraudRate" size={16} />
                </div>
                <div className="max-w-md text-sm text-slate-700 dark:text-slate-300">
                  of enterprise merchants report friendly fraud rising over three years.
                </div>
                <a className="inline-flex items-center gap-1 text-xs font-mono text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-white transition whitespace-nowrap font-medium" href="https://chargebacks911.com/2026-chargeback-field-report/" rel="noreferrer" target="_blank">
                  <span>Chargebacks911</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              <div className="p-6 rounded-2xl bg-white dark:bg-midnight-900/60 border border-slate-200 dark:border-slate-800/90 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-2">
                  <div className="text-4xl sm:text-5xl font-extrabold text-indigo-600 dark:text-indigo-400 font-mono tracking-tight">
                    38%
                  </div>
                  <InfoTip metricId="landing.costInfluence" size={16} />
                </div>
                <div className="max-w-md text-sm text-slate-700 dark:text-slate-300">
                  of merchants say chargeback costs now influence what they charge.
                </div>
                <a className="inline-flex items-center gap-1 text-xs font-mono text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-white transition whitespace-nowrap font-medium" href="https://greensheet.com/newswire&article_id=64300" rel="noreferrer" target="_blank">
                  <span>The Green Sheet</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              <div className="p-6 rounded-2xl bg-white dark:bg-midnight-900/60 border border-slate-200 dark:border-slate-800/90 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-2">
                  <div className="text-4xl sm:text-5xl font-extrabold text-sky-600 dark:text-cyan-300 font-mono tracking-tight">
                    58%
                  </div>
                  <InfoTip metricId="landing.aiShoppingExposure" size={16} />
                </div>
                <div className="max-w-md text-sm text-slate-700 dark:text-slate-300">
                  of UK online merchants believe AI-initiated purchases already reach them.
                </div>
                <a className="inline-flex items-center gap-1 text-xs font-mono text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-white transition whitespace-nowrap font-medium" href="https://www.retailgazette.co.uk/blog/2026/05/uk-retailers-warn-over-rising-ai-shopping-risks/" rel="noreferrer" target="_blank">
                  <span>Retail Gazette</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>

              <div className="p-6 rounded-2xl bg-white dark:bg-midnight-900/60 border border-slate-200 dark:border-slate-800/90 shadow-xs flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-center gap-2">
                  <div className="text-4xl sm:text-5xl font-extrabold text-slate-900 dark:text-blue-400 font-mono tracking-tight">
                    281.3M
                  </div>
                  <InfoTip metricId="landing.globalDisputesVolume" size={16} />
                </div>
                <div className="max-w-md text-sm text-slate-700 dark:text-slate-300">
                  global chargebacks estimated for 2026, from Mastercard and Datos data.
                </div>
                <a className="inline-flex items-center gap-1 text-xs font-mono text-slate-500 dark:text-slate-400 hover:text-blue-600 dark:hover:text-white transition whitespace-nowrap font-medium" href="https://www.chargeback.io/blog/chargeback-statistics" rel="noreferrer" target="_blank">
                  <span>Chargeback.io</span>
                  <GoogleIcon name="arrow_outward" size={13} />
                </a>
              </div>
            </div>

          </div>

          {/* Research Articles List */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mt-8 pt-8 border-t border-slate-200 dark:border-slate-800/80">
            <div className="p-5 rounded-xl bg-white dark:bg-midnight-900/40 border border-slate-200 dark:border-slate-800/80 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">Chargebacks911 <span className="text-slate-400 dark:text-slate-600">•</span> 30 Jun 2026</div>
                <div className="text-sm font-semibold text-slate-900 dark:text-white mt-1">Friendly fraud keeps climbing for large merchants</div>
                <p className="text-xs text-slate-600 dark:text-slate-400 mt-2 leading-relaxed">About one in four teams uses five or more separate tools just to investigate disputes.</p>
              </div>
              <div className="mt-4 pt-2">
                <a className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-1" href="https://chargebacks911.com/2026-chargeback-field-report/" target="_blank" rel="noreferrer">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a>
              </div>
            </div>

            <div className="p-5 rounded-xl bg-white dark:bg-midnight-900/40 border border-slate-200 dark:border-slate-800/80 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">eCommerceNews Asia <span className="text-slate-400 dark:text-slate-600">•</span> 2 Jul 2026</div>
                <div className="text-sm font-semibold text-slate-900 dark:text-white mt-1">Most merchants have no dedicated dispute team</div>
                <p className="text-xs text-slate-600 dark:text-slate-400 mt-2 leading-relaxed">Only about a third report a chargeback lead, and few track the card-network records behind their VAMP ratio.</p>
              </div>
              <div className="mt-4 pt-2">
                <a className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-1" href="https://ecommercenews.asia/story/friendly-fraud-drives-up-merchant-costs-report-finds" target="_blank" rel="noreferrer">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a>
              </div>
            </div>

            <div className="p-5 rounded-xl bg-white dark:bg-midnight-900/40 border border-slate-200 dark:border-slate-800/80 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">Forkast <span className="text-slate-400 dark:text-slate-600">•</span> Analysis</div>
                <div className="text-sm font-semibold text-slate-900 dark:text-white mt-1">Who pays when the AI agent buys wrong?</div>
                <p className="text-xs text-slate-600 dark:text-slate-400 mt-2 leading-relaxed">Agent payment protocols do not reallocate liability for non-fraud errors, and a draft EMVCo framework leaves it open.</p>
              </div>
              <div className="mt-4 pt-2">
                <a className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-1" href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a>
              </div>
            </div>

            <div className="p-5 rounded-xl bg-white dark:bg-midnight-900/40 border border-slate-200 dark:border-slate-800/80 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="text-[11px] font-mono text-slate-500 dark:text-slate-400">The Paypers <span className="text-slate-400 dark:text-slate-600">•</span> 24 Jun 2026</div>
                <div className="text-sm font-semibold text-slate-900 dark:text-white mt-1">Disputes and friendly fraud in the age of agentic commerce</div>
                <p className="text-xs text-slate-600 dark:text-slate-400 mt-2 leading-relaxed">A Juniper Research analyst describes a regulatory grey area and a missing source of truth for agent-led purchases.</p>
              </div>
              <div className="mt-4 pt-2">
                <a className="text-xs font-semibold text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-1" href="https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce" target="_blank" rel="noreferrer">
                  <span>Read</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a>
              </div>
            </div>
          </div>

        </div>
      </section>

      {/* ────────────────── HOW IT WORKS ────────────────── */}
      <section className="py-20 bg-white dark:bg-midnight-900/40 border-t border-slate-200 dark:border-white/[0.06]" id="how-it-works">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-3xl mb-14">
            <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-950 dark:text-white tracking-tight">How it works</h2>
            <p className="text-slate-600 dark:text-slate-400 text-lg mt-2 font-serif italic">Rules detect. AI investigates. Humans decide.</p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            <div className="bg-slate-50 dark:bg-midnight-950/80 border border-slate-200 dark:border-slate-800/90 rounded-2xl p-6 relative group hover:border-blue-300 dark:hover:border-blue-500/50 transition shadow-2xs">
              <div className="w-10 h-10 rounded-xl bg-blue-100 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-200 dark:border-blue-500/30 flex items-center justify-center font-mono text-sm font-bold mb-5">
                1
              </div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <span>Ingest</span>
              </h3>
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 mt-2.5 leading-relaxed">
                Signed PayPal webhooks land in a durable inbox. Duplicates are ignored.
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/80 border border-slate-200 dark:border-slate-800/90 rounded-2xl p-6 relative group hover:border-sky-300 dark:hover:border-sky-500/50 transition shadow-2xs">
              <div className="w-10 h-10 rounded-xl bg-sky-100 dark:bg-sky-500/10 text-sky-700 dark:text-sky-400 border border-sky-200 dark:border-sky-500/30 flex items-center justify-center font-mono text-sm font-bold mb-5">
                2
              </div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <span>Detect</span>
              </h3>
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 mt-2.5 leading-relaxed">
                Deterministic rules flag paid-not-marked, amount mismatch and duplicates.
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/80 border border-slate-200 dark:border-slate-800/90 rounded-2xl p-6 relative group hover:border-indigo-300 dark:hover:border-indigo-400/50 transition shadow-2xs">
              <div className="w-10 h-10 rounded-xl bg-indigo-100 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-400/30 flex items-center justify-center font-mono text-sm font-bold mb-5">
                3
              </div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <span>Investigate</span>
              </h3>
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 mt-2.5 leading-relaxed">
                An agent queries PayPal live with read-only tools and cites every record.
              </p>
            </div>

            <div className="bg-slate-50 dark:bg-midnight-950/80 border border-slate-200 dark:border-slate-800/90 rounded-2xl p-6 relative group hover:border-emerald-300 dark:hover:border-emerald-400/50 transition shadow-2xs">
              <div className="w-10 h-10 rounded-xl bg-emerald-100 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border border-emerald-200 dark:border-emerald-400/30 flex items-center justify-center font-mono text-sm font-bold mb-5">
                4
              </div>
              <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                <span>Approve</span>
              </h3>
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-400 mt-2.5 leading-relaxed">
                It proposes a safe action. An operator approves and the audit log records it.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── THE PRODUCT SHOWCASE ────────────────── */}
      <section className="py-20 md:py-28 relative bg-slate-50 dark:bg-midnight-950 border-t border-slate-200 dark:border-transparent" id="the-product">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="rounded-3xl bg-white dark:bg-gradient-to-b dark:from-blue-950/40 dark:via-midnight-900/60 dark:to-midnight-950/90 border border-slate-200 dark:border-blue-500/30 p-6 sm:p-10 lg:p-12 shadow-xs dark:shadow-2xl">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
              
              <div className="lg:col-span-5 space-y-6">
                <h2 className="text-3xl sm:text-4xl md:text-5xl font-extrabold text-slate-950 dark:text-white tracking-tight leading-tight">
                  One map. One investigator. Proof for every claim.
                </h2>
                <p className="text-slate-600 dark:text-slate-300 text-sm sm:text-base leading-relaxed">
                  Open any broken payment and see everything that touched it. Click Investigate and watch the agent query PayPal and your records, then answer in plain language.
                </p>
                <div className="space-y-4 pt-2">
                  <div className="flex items-start gap-3">
                    <div className="w-5 h-5 rounded-full bg-blue-100 dark:bg-blue-500/20 text-blue-700 dark:text-sky-400 flex items-center justify-center text-xs mt-0.5 font-bold">
                      ✓
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-900 dark:text-white">Every statement cites a record</h4>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Links go to the exact capture, event or order.</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="w-5 h-5 rounded-full bg-emerald-100 dark:bg-emerald-500/20 text-emerald-700 dark:text-emerald-400 flex items-center justify-center text-xs mt-0.5 font-bold">
                      ✓
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-900 dark:text-white">A verifier checks the AI</h4>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">Record IDs and amounts are validated before you see them.</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="w-5 h-5 rounded-full bg-amber-100 dark:bg-amber-500/20 text-amber-800 dark:text-amber-300 flex items-center justify-center text-xs mt-0.5 font-bold">
                      ✓
                    </div>
                    <div>
                      <h4 className="text-sm font-bold text-slate-900 dark:text-white">It admits when it does not know</h4>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">'Cause not determined' beats a confident guess.</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="lg:col-span-7">
                <div className="rounded-2xl bg-slate-50 dark:bg-midnight-950/90 border border-slate-200 dark:border-slate-700/80 p-5 shadow-lg relative overflow-hidden">
                  <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-200 dark:border-slate-800 text-xs font-mono text-slate-500 dark:text-slate-400">
                    <span className="text-slate-900 dark:text-slate-300 font-semibold">Case PR-0142</span>
                    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-indigo-50 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-500/30">
                      <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
                      Investigating
                    </span>
                  </div>

                  <div className="relative bg-white dark:bg-slate-950/80 rounded-xl p-6 min-h-[320px] flex flex-col justify-between border border-slate-200 dark:border-slate-800/80 shadow-xs">
                    <div className="flex justify-between items-start">
                      <span className="px-2.5 py-1 rounded bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-xs text-slate-700 dark:text-slate-300 font-medium">PayPal order</span>
                      <div className="text-center px-4 py-2 rounded-lg bg-blue-50 dark:bg-blue-950/50 border border-blue-200 dark:border-blue-500/40 text-xs font-mono font-bold text-blue-900 dark:text-white">
                        CAPTURE COMPLETED, $84.50
                      </div>
                      <span className="px-2.5 py-1 rounded bg-amber-50 dark:bg-amber-950/30 border border-amber-300 dark:border-amber-500/40 text-[11px] font-mono font-medium text-amber-800 dark:text-amber-300">Shop order #10482 still PENDING</span>
                    </div>

                    <div className="flex justify-center my-6">
                      <span className="px-3 py-1 rounded-md bg-slate-100 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 text-xs text-slate-700 dark:text-slate-400 font-medium">Webhook delivered twice</span>
                    </div>

                    <div className="flex justify-between text-[11px] font-mono text-slate-500">
                      <span>getPayPalOrder 140 ms</span>
                      <span>fastWebhookMatch</span>
                    </div>

                    <div className="absolute bottom-4 right-4 sm:right-6 w-72 bg-white dark:bg-slate-900/95 border border-slate-200 dark:border-slate-600 rounded-xl p-4 shadow-xl">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-xs font-bold text-slate-900 dark:text-white">Report</span>
                        <span className="text-[10px] font-mono text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-200 dark:border-emerald-500/20 font-semibold">Verified</span>
                      </div>
                      <p className="text-xs text-slate-600 dark:text-slate-300 mb-3">
                        <strong className="text-amber-700 dark:text-amber-400">Conflict:</strong> shop-order still PENDING after capture.
                      </p>
                      <div className="flex items-center gap-2">
                        <Link to="/queue" className="flex-1 py-1.5 px-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold text-center shadow transition">
                          Approve sync
                        </Link>
                        <button type="button" className="py-1.5 px-3 rounded-lg bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 text-xs font-medium border border-slate-200 dark:border-slate-700 transition">
                          Reject
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── THREE PAYMENTS THAT GO WRONG ────────────────── */}
      <section className="py-20 border-t border-slate-200 dark:border-white/[0.06] bg-white dark:bg-midnight-950/60">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl mb-12">
            <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-950 dark:text-white tracking-tight">Three payments that go wrong</h2>
            <p className="text-slate-600 dark:text-slate-400 text-base mt-2">
              PayPal, the order system, refunds and delivery each hold a piece. When they disagree, someone rebuilds the story by hand.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="p-6 rounded-2xl bg-slate-50 dark:bg-midnight-900/70 border border-slate-200 dark:border-slate-800/90 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="flex items-center gap-2 text-amber-600 dark:text-amber-400 text-sm font-bold mb-2">
                  <span className="w-2 h-2 rounded-full bg-amber-500" />
                  <span>Paid, but marked unpaid</span>
                </div>
                <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
                  A customer pays $84.50. The server crashes mid-webhook and order #10482 stays pending. Asking them to pay again would double-charge them.
                </p>
              </div>
              <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800/60 text-[11px] font-mono text-slate-500 dark:text-slate-400 font-medium">
                Detected: Webhook dropped • Action ready
              </div>
            </div>

            <div className="p-6 rounded-2xl bg-slate-50 dark:bg-midnight-900/70 border border-slate-200 dark:border-slate-800/90 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="flex items-center gap-2 text-sky-600 dark:text-cyan-400 text-sm font-bold mb-2">
                  <span className="w-2 h-2 rounded-full bg-sky-500" />
                  <span>The webhook that arrived twice</span>
                </div>
                <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
                  Without idempotency the order ships twice. PayResolve proves exactly one business action ran.
                </p>
              </div>
              <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800/60 text-[11px] font-mono text-slate-500 dark:text-slate-400 font-medium">
                Verified: Exactly-once state mutation
              </div>
            </div>

            <div className="p-6 rounded-2xl bg-slate-50 dark:bg-midnight-900/70 border border-slate-200 dark:border-slate-800/90 hover:border-slate-300 dark:hover:border-slate-700 transition flex flex-col justify-between shadow-2xs">
              <div>
                <div className="flex items-center gap-2 text-indigo-600 dark:text-indigo-400 text-sm font-bold mb-2">
                  <span className="w-2 h-2 rounded-full bg-indigo-500" />
                  <span>Where is my refund?</span>
                </div>
                <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
                  Requested, pending and completed look identical to a customer. It separates them before anyone refunds twice.
                </p>
              </div>
              <div className="mt-6 pt-4 border-t border-slate-200 dark:border-slate-800/60 text-[11px] font-mono text-slate-500 dark:text-slate-400 font-medium">
                Resolved: Lifecycle disambiguation
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── WHY WE CHOSE THIS PROBLEM ────────────────── */}
      <section className="py-20 md:py-28 relative bg-slate-50 dark:bg-midnight-950 border-t border-slate-200 dark:border-transparent">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-2xl mb-12">
            <h2 className="text-3xl sm:text-4xl font-extrabold text-slate-950 dark:text-white tracking-tight">Why we chose this problem</h2>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-start">
            <div className="lg:col-span-8 overflow-hidden rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-midnight-900/60 shadow-xs">
              <table className="w-full text-left text-xs sm:text-sm">
                <thead className="bg-slate-50 dark:bg-midnight-950/80 border-b border-slate-200 dark:border-slate-800 text-[11px] font-mono uppercase text-slate-600 dark:text-slate-400">
                  <tr>
                    <th className="py-3.5 px-4 sm:px-6 font-semibold">Judging criterion</th>
                    <th className="py-3.5 px-4 sm:px-6 font-semibold">How PayResolve answers it</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-800/60 text-slate-700 dark:text-slate-300 font-sans">
                  <tr>
                    <td className="py-4 px-4 sm:px-6 font-semibold text-slate-900 dark:text-white">Technological implementation</td>
                    <td className="py-4 px-4 sm:px-6 text-slate-600 dark:text-slate-300">Real Sandbox capture, signed webhooks, idempotency, tool-calling AI with a verifier</td>
                  </tr>
                  <tr>
                    <td className="py-4 px-4 sm:px-6 font-semibold text-slate-900 dark:text-white">Design</td>
                    <td className="py-4 px-4 sm:px-6 text-slate-600 dark:text-slate-300">Queue, case, graph, approve: one complete operator workflow</td>
                  </tr>
                  <tr>
                    <td className="py-4 px-4 sm:px-6 font-semibold text-slate-900 dark:text-white">Potential impact</td>
                    <td className="py-4 px-4 sm:px-6 text-slate-600 dark:text-slate-300">Daily merchant pain with real cost: double charges, double refunds, missed disputes</td>
                  </tr>
                  <tr>
                    <td className="py-4 px-4 sm:px-6 font-semibold text-slate-900 dark:text-white">Innovation</td>
                    <td className="py-4 px-4 sm:px-6 text-slate-600 dark:text-slate-300">Provenance and safe actions, not a risk score or a chatbot</td>
                  </tr>
                  <tr>
                    <td className="py-4 px-4 sm:px-6 font-semibold text-slate-900 dark:text-white">Presentation</td>
                    <td className="py-4 px-4 sm:px-6 text-slate-600 dark:text-slate-300">One story: paid, but the system says unpaid</td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div className="lg:col-span-4 p-6 sm:p-7 rounded-2xl bg-white dark:bg-midnight-900/80 border border-slate-200 dark:border-slate-700/80 shadow-xs">
              <div className="text-xs font-mono uppercase tracking-wider text-blue-600 dark:text-sky-400 font-semibold">
                Thesis
              </div>
              <h3 className="text-lg font-bold text-slate-900 dark:text-white mt-1">Why now</h3>
              <p className="text-xs sm:text-sm text-slate-600 dark:text-slate-300 leading-relaxed mt-4">
                AI agents are starting to buy things for customers, and nobody has settled who is liable when they get it wrong. Merchants will need records they can trust. Read the{' '}
                <a className="text-blue-600 dark:text-blue-400 font-semibold hover:underline inline-flex items-center gap-0.5" href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer">
                  <span>Forkast analysis</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a>, and why agent-led disputes lack a source of truth in{' '}
                <a className="text-blue-600 dark:text-blue-400 font-semibold hover:underline inline-flex items-center gap-0.5" href="https://www.chargeflow.io/blog/ai-agent-chargeback-liability" target="_blank" rel="noreferrer">
                  <span>Chargeflow</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a> and{' '}
                <a className="text-blue-600 dark:text-blue-400 font-semibold hover:underline inline-flex items-center gap-0.5" href="https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce" target="_blank" rel="noreferrer">
                  <span>The Paypers</span>
                  <GoogleIcon name="arrow_outward" size={12} />
                </a>.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── SOURCES & CITATIONS ────────────────── */}
      <section className="py-16 bg-white dark:bg-midnight-950 border-t border-slate-200 dark:border-white/[0.06]" id="sources">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="mb-8">
            <h2 className="text-2xl font-bold text-slate-950 dark:text-white tracking-tight">Sources and references</h2>
            <p className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1">Every figure on this page comes from the articles below.</p>
          </div>
          <div className="space-y-3 font-mono text-[11px] sm:text-xs text-slate-500 dark:text-slate-400 leading-relaxed">
            <div>
              1. Chargebacks911, 2026 Chargeback Field Report, 30 Jun 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://chargebacks911.com/2026-chargeback-field-report/" rel="noreferrer" target="_blank">
                https://chargebacks911.com/2026-chargeback-field-report/
              </a>
            </div>
            <div>
              2. The Green Sheet, Chargebacks are quietly raising prices, 28 Jul 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://greensheet.com/newswire&article_id=64300" rel="noreferrer" target="_blank">
                https://greensheet.com/newswire&article_id=64300
              </a>
            </div>
            <div>
              3. eCommerceNews Asia, Friendly fraud drives up merchant costs, 2 Jul 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://ecommercenews.asia/story/friendly-fraud-drives-up-merchant-costs-report-finds" rel="noreferrer" target="_blank">
                https://ecommercenews.asia/story/friendly-fraud-drives-up-merchant-costs-report-finds
              </a>
            </div>
            <div>
              4. Retail Gazette, UK retailers raise concerns over agentic AI shopping risks, May 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://www.retailgazette.co.uk/blog/2026/05/uk-retailers-warn-over-rising-ai-shopping-risks/" rel="noreferrer" target="_blank">
                https://www.retailgazette.co.uk/blog/2026/05/uk-retailers-warn-over-rising-ai-shopping-risks/
              </a>
            </div>
            <div>
              5. Forkast, The agentic commerce liability gap{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" rel="noreferrer" target="_blank">
                https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/
              </a>
            </div>
            <div>
              6. The Paypers, Disputes and friendly fraud in the age of agentic commerce, 24 Jun 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce" rel="noreferrer" target="_blank">
                https://thepaypers.com/payments/expert-views/disputes-and-friendly-fraud-in-the-age-of-agentic-commerce
              </a>
            </div>
            <div>
              7. Chargeflow, AI agent chargeback liability, 7 Jul 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://www.chargeflow.io/blog/ai-agent-chargeback-liability" rel="noreferrer" target="_blank">
                https://www.chargeflow.io/blog/ai-agent-chargeback-liability
              </a>
            </div>
            <div>
              8. Chargeback.io, Chargeback statistics 2026{' '}
              <a className="text-blue-600 dark:text-blue-400 hover:underline ml-1 break-all" href="https://www.chargeback.io/blog/chargeback-statistics" rel="noreferrer" target="_blank">
                https://www.chargeback.io/blog/chargeback-statistics
              </a>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── BOTTOM CTA ────────────────── */}
      <section className="py-16 md:py-24 bg-slate-50 dark:bg-midnight-950 border-t border-slate-200 dark:border-transparent">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="rounded-3xl bg-blue-600 dark:bg-gradient-to-r dark:from-blue-700 dark:via-indigo-700 dark:to-sky-600 p-8 sm:p-14 text-white relative overflow-hidden shadow-xl dark:shadow-2xl">
            <div className="absolute -right-20 -bottom-20 w-80 h-80 rounded-full bg-white/10 blur-3xl pointer-events-none" />
            <div className="max-w-2xl relative z-10">
              <h2 className="text-3xl sm:text-4xl md:text-5xl font-extrabold tracking-tight leading-tight">
                See a broken payment explained in under a minute.
              </h2>
              <p className="text-base sm:text-lg text-blue-100 font-normal mt-4">
                Real Sandbox payments. Real webhooks. No real money.
              </p>
              <div className="flex flex-wrap items-center gap-4 mt-8">
                <Link
                  to="/queue"
                  className="px-6 py-3 rounded-full text-sm font-semibold bg-white text-blue-900 hover:bg-slate-100 transition shadow cursor-pointer"
                >
                  Open workspace
                </Link>
                <Link
                  to="/signin"
                  className="px-6 py-3 rounded-full text-sm font-semibold bg-blue-700 dark:bg-blue-800/80 hover:bg-blue-800 text-white border border-blue-400/40 transition cursor-pointer"
                >
                  Sign in
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ────────────────── FOOTER ────────────────── */}
      <footer className="border-t border-slate-200 dark:border-white/[0.08] bg-white dark:bg-midnight-950 pt-16 pb-12 text-slate-500 dark:text-slate-400 text-xs">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-2 md:grid-cols-6 gap-8 mb-12">
            
            {/* Brand Summary */}
            <div className="col-span-2">
              <div className="flex items-center gap-2 mb-3">
                <div className="w-6 h-6 rounded-md bg-blue-600 flex items-center justify-center text-white font-bold text-xs">
                  <PayResolveMark size={14} />
                </div>
                <span className="text-slate-900 dark:text-white font-bold text-base">PayResolve</span>
                <span className="px-1 py-0.5 rounded text-[9px] font-mono bg-blue-50 dark:bg-blue-500/20 text-blue-700 dark:text-sky-400 border border-blue-200 dark:border-blue-500/30 uppercase font-semibold">
                  AI
                </span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 max-w-sm leading-relaxed mb-4">
                Investigate PayPal payment exceptions with evidence you can trust. AI proposes, humans approve.
              </p>
              <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-[11px] font-mono text-emerald-700 dark:text-emerald-400 font-semibold">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                <span>Sandbox only. No real money.</span>
              </div>
            </div>

            {/* Links Column 1: Product */}
            <div>
              <h4 className="text-slate-900 dark:text-slate-200 font-semibold mb-3">Product</h4>
              <ul className="space-y-2">
                <li><Link className="hover:text-blue-600 dark:hover:text-white transition" to="/queue">Exception queue</Link></li>
                <li><Link className="hover:text-blue-600 dark:hover:text-white transition" to="/case/PR-0142">Case workspace</Link></li>
                <li><Link className="hover:text-blue-600 dark:hover:text-white transition" to="/events">Event inbox</Link></li>
                <li><Link className="hover:text-blue-600 dark:hover:text-white transition" to="/shop">Demo shop</Link></li>
                <li><Link className="hover:text-blue-600 dark:hover:text-white transition" to="/actions">Actions</Link></li>
                <li><Link className="hover:text-blue-600 dark:hover:text-white transition" to="/benchmark">Benchmark</Link></li>
              </ul>
            </div>

            {/* Links Column 2: Project */}
            <div>
              <h4 className="text-slate-900 dark:text-slate-200 font-semibold mb-3">Project</h4>
              <ul className="space-y-2">
                <li><a className="hover:text-blue-600 dark:hover:text-white transition" href="#how-it-works">Product requirements</a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition" href="#how-it-works">Architecture</a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition" href="#how-it-works">Build phases</a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition" href="#how-it-works">Engineering rules</a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition" href="https://github.com/Skedare240507/PayResolve-AI" target="_blank" rel="noreferrer">GitHub repository</a></li>
              </ul>
            </div>

            {/* Links Column 3: Hackathon */}
            <div>
              <h4 className="text-slate-900 dark:text-slate-200 font-semibold mb-3">Hackathon</h4>
              <ul className="space-y-2">
                <li><a className="hover:text-blue-600 dark:hover:text-white transition inline-flex items-center gap-0.5" href="https://devpost.com" target="_blank" rel="noreferrer"><span>PayPal AI Hackathon</span><GoogleIcon name="arrow_outward" size={11} /></a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition inline-flex items-center gap-0.5" href="https://devpost.com" target="_blank" rel="noreferrer"><span>Official rules</span><GoogleIcon name="arrow_outward" size={11} /></a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition inline-flex items-center gap-0.5" href="https://devpost.com" target="_blank" rel="noreferrer"><span>Project gallery</span><GoogleIcon name="arrow_outward" size={11} /></a></li>
              </ul>
            </div>

            {/* Links Column 4: Research */}
            <div>
              <h4 className="text-slate-900 dark:text-slate-200 font-semibold mb-3">Research</h4>
              <ul className="space-y-2">
                <li><a className="hover:text-blue-600 dark:hover:text-white transition inline-flex items-center gap-0.5" href="https://chargebacks911.com/2026-chargeback-field-report/" target="_blank" rel="noreferrer"><span>2026 Chargeback Field Report</span><GoogleIcon name="arrow_outward" size={11} /></a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition inline-flex items-center gap-0.5" href="https://www.chargeback.io/blog/chargeback-statistics" target="_blank" rel="noreferrer"><span>Chargeback statistics</span><GoogleIcon name="arrow_outward" size={11} /></a></li>
                <li><a className="hover:text-blue-600 dark:hover:text-white transition inline-flex items-center gap-0.5" href="https://forkast.news/the-agentic-commerce-liability-gap-who-pays-when-the-agent-buys-wrong/" target="_blank" rel="noreferrer"><span>Agentic liability gap</span><GoogleIcon name="arrow_outward" size={11} /></a></li>
              </ul>
            </div>

          </div>

          {/* Bottom Legal Line */}
          <div className="pt-8 border-t border-slate-200 dark:border-slate-900 flex flex-col sm:flex-row items-center justify-between gap-4 text-slate-500 text-[11px]">
            <div>
              &copy; 2026 PayResolve AI. MIT licensed. Built for the PayPal AI Hackathon.
            </div>
            <div className="flex gap-4">
              <a className="hover:text-slate-900 dark:hover:text-slate-400" href="#sources">Privacy</a>
              <a className="hover:text-slate-900 dark:hover:text-slate-400" href="#sources">Security</a>
              <a className="hover:text-slate-900 dark:hover:text-slate-400" href="https://github.com/Skedare240507/PayResolve-AI/blob/main/LICENSE" target="_blank" rel="noreferrer">Licence</a>
              <Link className="hover:text-slate-900 dark:hover:text-slate-400" to="/status">Status</Link>
            </div>
          </div>
          <div className="mt-2 text-[10px] text-slate-400">
            PayResolve AI is an independent hackathon project. It is not affiliated with, endorsed by or sponsored by PayPal, Inc. PayPal is a trademark of PayPal, Inc. All payment data shown is synthetic Sandbox data.
          </div>
        </div>
      </footer>

    </div>
  );
}
