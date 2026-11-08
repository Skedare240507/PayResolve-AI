export const GoogleIcon = ({
  name,
  className = '',
  size = 20,
  filled = false,
}: {
  name: string;
  className?: string;
  size?: number;
  filled?: boolean;
}) => {
  return (
    <span
      className={`material-symbols-outlined select-none leading-none inline-flex items-center justify-center ${className}`}
      style={{
        fontSize: `${size}px`,
        fontVariationSettings: filled
          ? "'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24"
          : "'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24",
      }}
      aria-hidden="true"
    >
      {name}
    </span>
  );
};
